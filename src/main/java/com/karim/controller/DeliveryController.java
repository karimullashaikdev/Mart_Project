package com.karim.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karim.dto.LocationUpdateDto;
import com.karim.dto.OrderNotificationDto;
import com.karim.service.DeliveryService;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Delivery Agent API  —  /api/delivery
 *
 * Full lifecycle handled here (agent side):
 *
 *   GET  /api/delivery/orders/available
 *        → Returns orders in PROCESSING status (ready to be picked up)
 *
 *   POST /api/delivery/orders/{orderId}/accept
 *        → Agent accepts order → status moves PROCESSING → DISPATCHED
 *
 *   POST /api/delivery/orders/{orderId}/start
 *        → Agent starts riding → status moves DISPATCHED → OUT_FOR_DELIVERY
 *
 *   POST /api/delivery/orders/{orderId}/complete
 *        → Agent submits OTP → OTP verified → status moves OUT_FOR_DELIVERY → DELIVERED
 *
 *   GET  /api/delivery/orders/my
 *        → Returns all orders assigned to this delivery agent
 *
 *   POST /api/delivery/orders/{orderId}/location
 *        → Agent pushes live GPS coordinates while delivering
 *
 *   POST /api/delivery/orders/{orderId}/location/stop
 *        → Agent stops location sharing after delivery
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    /**
     * Request body DTO for completing delivery with OTP.
     */
    @Data
    public static class CompleteDeliveryRequestDto {
        private String otp;
    }

    /**
     * Available orders — PROCESSING status, not yet accepted by any agent.
     * This is what appears in the "Available Orders" panel on delivery.html.
     */
    @GetMapping("/orders/available")
    public ResponseEntity<List<OrderNotificationDto>> getAvailableOrders(
            @RequestHeader("X-User-Id") UUID agentId) {
        return ResponseEntity.ok(deliveryService.getAvailableOrders(agentId));
    }

    /**
     * Agent accepts an order.
     * Transition: PROCESSING → DISPATCHED
     * Also assigns the order to this agent in the DeliveryAssignment table.
     */
    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<Void> acceptOrder(
            @RequestHeader("X-User-Id") UUID agentId,
            @PathVariable UUID orderId) {
        deliveryService.acceptOrder(orderId, agentId);
        return ResponseEntity.ok().build();
    }

    /**
     * Agent clicks "Start Delivery" — they have picked up the package and are riding.
     * Transition: DISPATCHED → OUT_FOR_DELIVERY
     */
    @PostMapping("/orders/{orderId}/start")
    public ResponseEntity<Void> startDelivery(
            @RequestHeader("X-User-Id") UUID agentId,
            @PathVariable UUID orderId) {
        deliveryService.startDelivery(orderId, agentId);
        return ResponseEntity.ok().build();
    }

    /**
     * Agent has reached the customer and entered the OTP.
     * OTP is validated here. If valid:
     * Transition: OUT_FOR_DELIVERY → DELIVERED
     *
     * Request body: { "otp": "1234" }
     */
    @PostMapping("/orders/{orderId}/complete")
    public ResponseEntity<Void> completeDelivery(
            @RequestHeader("X-User-Id") UUID agentId,
            @PathVariable UUID orderId,
            @RequestBody CompleteDeliveryRequestDto request) {
        deliveryService.completeDelivery(orderId, agentId, request.getOtp());
        return ResponseEntity.ok().build();
    }

    /**
     * Orders assigned to this agent (all statuses: DISPATCHED, OUT_FOR_DELIVERY, DELIVERED).
     * Powers the "My Orders" panel on delivery.html.
     */
    @GetMapping("/orders/my")
    public ResponseEntity<List<OrderNotificationDto>> getMyOrders(
            @RequestHeader("X-User-Id") UUID agentId) {
        return ResponseEntity.ok(deliveryService.getMyOrders(agentId));
    }

    /**
     * Agent sends live GPS coordinates while delivering.
     * Stored or broadcast via WebSocket to allow real-time tracking.
     * Request body: { "orderId": "...", "latitude": 17.38, "longitude": 78.49 }
     */
    @PostMapping("/orders/{orderId}/location")
    public ResponseEntity<Void> updateLocation(
            @RequestHeader("X-User-Id") UUID agentId,
            @PathVariable UUID orderId,
            @RequestBody LocationUpdateDto locationDto) {
        deliveryService.updateAgentLocation(orderId, agentId, locationDto.getLatitude(), locationDto.getLongitude());
        return ResponseEntity.ok().build();
    }

    /**
     * Agent stops location sharing (called after delivery complete or on logout).
     */
    @PostMapping("/orders/{orderId}/location/stop")
    public ResponseEntity<Void> stopLocation(
            @RequestHeader("X-User-Id") UUID agentId,
            @PathVariable UUID orderId) {
        deliveryService.stopAgentLocation(orderId, agentId);
        return ResponseEntity.ok().build();
    }
}