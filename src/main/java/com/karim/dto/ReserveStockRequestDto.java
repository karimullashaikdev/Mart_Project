package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class ReserveStockRequestDto {
	private Integer quantity;
	private UUID orderId;
}
