package com.karim.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponse {

	private UUID paymentId;

	private String paymentReference;

	private String status;

	private String message;

	private Float amount;

	private String method;

	// only for COD/manual/internal testing flow
	private String otp;

	// Razorpay details
	private String gatewayName;
	private String gatewayKeyId;
	private String gatewayOrderId;
	private String gatewayPaymentId;
	private String gatewaySignature;
	private String gatewayTxnId;
}