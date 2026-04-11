package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class ReleaseReservedStockRequestDto {
    private Integer quantity;
    private UUID orderId;
}