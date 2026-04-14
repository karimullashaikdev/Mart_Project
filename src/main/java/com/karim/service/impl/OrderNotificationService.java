package com.karim.service.impl;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.karim.dto.OrderItemResponseDto;
import com.karim.dto.OrderNotificationDto;
import com.karim.entity.Order;
import com.karim.repository.OrderItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

	private final SimpMessagingTemplate messagingTemplate;
	private final OrderItemRepository orderItemRepository;

	public void notifyNewOrder(Order order) {
		System.out.println(
				"=== [DEBUG] notifyNewOrder() called for order: " + order.getId() + " | Status: " + order.getStatus());

		OrderNotificationDto dto = mapToDto(order);

		try {
			messagingTemplate.convertAndSend("/topic/delivery-orders", dto);
			System.out.println(
					"=== [SUCCESS] WebSocket message sent to /topic/delivery-orders for order: " + order.getId());
		} catch (Exception e) {
			System.err.println("=== [ERROR] Failed to send WebSocket message for order " + order.getId());
			e.printStackTrace();
		}
	}

	private OrderNotificationDto mapToDto(Order order) {

		String customerName = null;
		String customerPhone = null;
		if (order.getUser() != null) {
			customerName = order.getUser().getFullName();
			customerPhone = order.getUser().getPhone();
		}

		String addressLine = order.getAddress() != null
				? order.getAddress().getLine1() + ", " + order.getAddress().getCity()
				: null;
		String city = null;
		String pincode = null;
		// FIX: read lat/lng from Address entity
		Double latitude = null;
		Double longitude = null;
		if (order.getAddress() != null) {
			addressLine = order.getAddress().getLine1();
			city = order.getAddress().getCity();
			pincode = order.getAddress().getPincode();
			// populate these if your Address entity has coordinate fields
			latitude = order.getAddress().getLatitude();
			longitude = order.getAddress().getLongitude();
		}

		List<OrderItemResponseDto> itemDtos = orderItemRepository.findByOrder_Id(order.getId()).stream()
				.map(item -> OrderItemResponseDto.builder().itemId(item.getId()).productId(item.getProduct().getId())
						.productName(item.getProduct().getName()).quantity(item.getQuantity())
						.unitPrice(item.getUnitPrice()).lineTotal(item.getLineTotal())
						.status(item.getItemStatus() != null ? item.getItemStatus().name() : null).build())
				.toList();

		return OrderNotificationDto.builder().orderId(order.getId()).orderNumber(order.getOrderNumber())
				.status(order.getStatus().name()).customerName(customerName).customerPhone(customerPhone)
				.deliveryAddress(addressLine).deliveryCity(city).deliveryPincode(pincode).latitude(latitude)
				.longitude(longitude).subtotal(order.getSubtotal()).taxAmount(order.getTaxAmount())
				.deliveryFee(order.getDeliveryFee()).totalAmount(order.getTotalAmount()).placedAt(order.getPlacedAt())
				.confirmedAt(order.getConfirmedAt()).items(itemDtos).build();
	}
}