package com.karim.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponseDto {

	private UUID itemId;
	private UUID productId;
	private String productName;

	private Integer quantity;
	private Double unitPrice;
	private Double lineTotal;
	private String status;
}
