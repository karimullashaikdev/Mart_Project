package com.karim.dto;

import lombok.Data;

@Data
public class AdjustStockRequestDto {
	private Integer delta;
	private String reason;
}
