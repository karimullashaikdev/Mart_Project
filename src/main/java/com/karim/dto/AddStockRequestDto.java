package com.karim.dto;

import lombok.Data;

@Data
public class AddStockRequestDto {
	private Integer quantity;
	private String reason;
}
