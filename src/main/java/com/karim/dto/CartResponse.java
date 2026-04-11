package com.karim.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class CartResponse {
    private UUID id;
    private UUID userId;
    private List<CartItemResponse> items;
    private int itemCount;
    private BigDecimal subtotal;
    private String couponCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}