package com.karim.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderNotificationDto {
    private UUID orderId;
    private String orderNumber;
    private String status;

    private String customerName;
    private String customerPhone;

    private String deliveryAddress;
    private String deliveryCity;
    private String deliveryPincode;

    private Double latitude;
    private Double longitude;

    private Double subtotal;
    private Double taxAmount;
    private Double deliveryFee;
    private Double totalAmount;

    private LocalDateTime placedAt;
    private LocalDateTime confirmedAt;

    private List<OrderItemResponseDto> items;
}