package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class RestockFromReturnRequestDto {
	private Integer quantity;
	private UUID returnRequestId;
}