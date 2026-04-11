package com.karim.dto;

import com.karim.enums.OrderStatus;

import lombok.Data;

@Data
public class OrderStatusUpdateRequestDto {
	private OrderStatus status;
}