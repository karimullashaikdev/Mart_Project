package com.karim.dto;

import java.util.UUID;

import com.karim.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitiatePaymentRequest {

	@NotNull
	private UUID orderId;

	@NotNull
	private PaymentMethod method;
}
