package com.karim.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.karim.dto.AdminOrderDto;
import com.karim.dto.OrderFilter;
import com.karim.dto.OrderItemResponseDto;
import com.karim.dto.OrderResponseDto;
import com.karim.dto.OrderSummaryDto;
import com.karim.dto.PlaceOrderRequestDto;
import com.karim.entity.Order;
import com.karim.enums.OrderItemStatus;
import com.karim.enums.OrderStatus;

public interface OrderService {

	Order placeOrder(UUID userId, PlaceOrderRequestDto dto, UUID actorId);

	OrderResponseDto getOrder(UUID orderId, UUID userId);

	OrderResponseDto getOrderByNumber(String orderNumber);

	Page<OrderSummaryDto> listOrdersByUser(UUID userId, OrderFilter filter, Pageable pageable);

	Page<AdminOrderDto> listOrdersAdmin(OrderFilter filter, Pageable pageable);

	void confirmOrder(UUID orderId, UUID actorId);

	void markProcessing(UUID orderId, UUID actorId);

	void markDispatched(UUID orderId, UUID actorId);

	void markOutForDelivery(UUID orderId, UUID actorId);

	void markDelivered(UUID orderId, UUID actorId);

	void cancelOrder(UUID orderId, String reason, UUID actorId);

	void updateOrderStatus(UUID orderId, OrderStatus newStatus, UUID actorId);

	void softDeleteOrder(UUID orderId, UUID actorId);

	List<OrderItemResponseDto> getOrderItems(UUID orderId);

	void updateOrderItemStatus(UUID itemId, OrderItemStatus newStatus, UUID actorId);
}
