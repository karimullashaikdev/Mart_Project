package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.karim.enums.StockStatus;

import lombok.Data;

@Data
public class StockResponseDto {

	private UUID stockId;
	private UUID productId;

	private Integer quantityAvailable;
	private Integer quantityReserved;

	private Integer reorderLevel;
	private Integer reorderQuantity;

	private StockStatus status;
	private LocalDateTime lastUpdatedAt;
}