package com.karim.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class CartSummaryResponse {
    private UUID cartId;
    private List<CartItemResponse> items;
    private BigDecimal subtotal;
    private String couponCode;
}