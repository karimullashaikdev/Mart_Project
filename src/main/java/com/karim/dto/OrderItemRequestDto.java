package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class OrderItemRequestDto {

    private UUID productId;
    private Integer quantity;
}
