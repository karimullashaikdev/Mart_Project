package com.karim.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponseDto {

    private UUID orderId;
    private String orderNumber;
    private String status;

    private Double subtotal;
    private Double taxAmount;
    private Double deliveryFee;
    private Double totalAmount;

    private LocalDateTime placedAt;

    private List<OrderItemResponseDto> items;
}
