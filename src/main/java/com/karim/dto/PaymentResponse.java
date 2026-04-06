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

	// 🔐 Only for testing (remove in production later)
	private String otp;

	// 🔮 Future gateway integration (Razorpay etc.)
	private String gatewayOrderId;

	private String gatewayTxnId;
}