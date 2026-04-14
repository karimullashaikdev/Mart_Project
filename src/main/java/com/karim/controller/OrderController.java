package com.karim.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karim.dto.AdminOrderDto;
import com.karim.dto.CancelOrderRequestDto;
import com.karim.dto.OrderFilter;
import com.karim.dto.OrderItemResponseDto;
import com.karim.dto.OrderItemStatusUpdateRequestDto;
import com.karim.dto.OrderResponseDto;
import com.karim.dto.OrderSummaryDto;
import com.karim.dto.PlaceOrderRequestDto;
import com.karim.entity.Order;
import com.karim.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class OrderController {

    private final OrderService orderService;

    // =========================================================================
    // USER APIs  —  /api/user/orders
    // =========================================================================

    /**
     * Place an order. Internally:
     *   PENDING → CONFIRMED (immediately, same transaction)
     *   CONFIRMED → PROCESSING (auto, after 5 minutes via scheduler)
     * No manual step needed from admin or user after this call.
     */
    @PostMapping("/api/user/orders")
    public ResponseEntity<OrderResponseDto> placeOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Id", required = false) UUID actorId,
            @Valid @RequestBody PlaceOrderRequestDto request) {

        UUID effectiveActorId = actorId != null ? actorId : userId;
        Order savedOrder = orderService.placeOrder(userId, request, effectiveActorId);
        // Re-fetch so the response shows CONFIRMED status (auto-confirm happened inside placeOrder)
        OrderResponseDto response = orderService.getOrder(savedOrder.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/user/orders")
    public ResponseEntity<Page<OrderSummaryDto>> listMyOrders(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) com.karim.enums.OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        OrderFilter filter = new OrderFilter();
        filter.setStatus(status);
        filter.setFromDate(fromDate);
        filter.setToDate(toDate);

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(orderService.listOrdersByUser(userId, filter, pageable));
    }

    @GetMapping("/api/user/orders/{orderId}")
    public ResponseEntity<OrderResponseDto> getMyOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId, userId));
    }

    @GetMapping("/api/user/orders/number/{orderNumber}")
    public ResponseEntity<OrderResponseDto> getOrderByNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByNumber(orderNumber));
    }

    @GetMapping("/api/user/orders/{orderId}/items")
    public ResponseEntity<List<OrderItemResponseDto>> getMyOrderItems(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID orderId) {
        orderService.getOrder(orderId, userId); // ownership check
        return ResponseEntity.ok(orderService.getOrderItems(orderId));
    }

    @PatchMapping("/api/user/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancelMyOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Actor-Id", required = false) UUID actorId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequestDto request) {

        orderService.getOrder(orderId, userId); // ownership check
        UUID effectiveActorId = actorId != null ? actorId : userId;
        String reason = request != null ? request.getReason() : null;
        orderService.cancelOrder(orderId, reason, effectiveActorId);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // ADMIN APIs  —  /api/admin/orders
    //
    // What admin can do:
    //   ✅ View all orders + items
    //   ✅ Delete (soft) cancelled orders
    //   ✅ Update item return status (RETURNED / PARTIALLY_RETURNED)
    //   ✅ updateOrderStatus — generic, validates ALLOWED_TRANSITIONS guard
    //
    // What admin CANNOT do anymore:
    //   ❌ Manually confirm (auto on placeOrder)
    //   ❌ Manually move to processing (auto after 5 min)
    //   ❌ Skip steps (ALLOWED_TRANSITIONS blocks it)
    //
    // What delivery agent does (in DeliveryController, not here):
    //   🛵 Accept order → DISPATCHED
    //   🛵 Start delivery → OUT_FOR_DELIVERY
    //   🛵 OTP verified → DELIVERED
    // =========================================================================

    @GetMapping("/api/admin/orders")
    public ResponseEntity<Page<AdminOrderDto>> listOrdersForAdmin(
            @RequestParam(required = false) com.karim.enums.OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        OrderFilter filter = new OrderFilter();
        filter.setStatus(status);
        filter.setFromDate(fromDate);
        filter.setToDate(toDate);

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(orderService.listOrdersAdmin(filter, pageable));
    }

    @GetMapping("/api/admin/orders/{orderId}/items")
    public ResponseEntity<List<OrderItemResponseDto>> getOrderItemsForAdmin(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderItems(orderId));
    }

    /**
     * Generic status update for admin — still guarded by ALLOWED_TRANSITIONS.
     * Admin cannot skip steps even through this endpoint.
     * Primary use: edge-case manual overrides (e.g. cancelling a stuck order).
     */
    @PatchMapping("/api/admin/orders/{orderId}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @RequestHeader("X-Actor-Id") UUID actorId,
            @PathVariable UUID orderId,
            @Valid @RequestBody com.karim.dto.OrderStatusUpdateRequestDto request) {
        orderService.updateOrderStatus(orderId, request.getStatus(), actorId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/admin/orders/{orderId}")
    public ResponseEntity<Void> softDeleteOrder(
            @RequestHeader("X-Actor-Id") UUID actorId,
            @PathVariable UUID orderId) {
        orderService.softDeleteOrder(orderId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/order-items/{itemId}/status")
    public ResponseEntity<Void> updateOrderItemStatus(
            @RequestHeader("X-Actor-Id") UUID actorId,
            @PathVariable UUID itemId,
            @Valid @RequestBody com.karim.dto.OrderItemStatusUpdateRequestDto request) {
        orderService.updateOrderItemStatus(itemId, request.getStatus(), actorId);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // REMOVED ENDPOINTS — these are now handled automatically:
    //
    //   DELETE  /api/admin/orders/{orderId}/confirm
    //     → Auto-called in placeOrder() via system actor
    //
    //   DELETE  /api/admin/orders/{orderId}/processing
    //     → Auto-called by OrderProcessingScheduler after 5 minutes
    //
    //   DELETE  /api/admin/orders/{orderId}/dispatch
    //     → Called by DeliveryController when agent accepts order
    //
    //   DELETE  /api/admin/orders/{orderId}/out-for-delivery
    //     → Called by DeliveryController when agent starts delivery
    //
    //   DELETE  /api/admin/orders/{orderId}/deliver
    //     → Called by DeliveryController after OTP verified
    // =========================================================================
}