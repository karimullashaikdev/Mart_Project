package com.karim.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyRazorpayPaymentRequest {

	@NotNull
	private UUID paymentId;

	@NotBlank
	private String razorpayOrderId;

	@NotBlank
	private String razorpayPaymentId;

	@NotBlank
	private String razorpaySignature;
}
