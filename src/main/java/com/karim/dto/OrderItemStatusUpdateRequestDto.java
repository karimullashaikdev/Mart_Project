package com.karim.dto;

import com.karim.enums.OrderItemStatus;

import lombok.Data;

@Data
public class OrderItemStatusUpdateRequestDto {
	private OrderItemStatus status;
}
