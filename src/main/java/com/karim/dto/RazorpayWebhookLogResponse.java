package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RazorpayWebhookLogResponse {
	private UUID id;
	private String eventId;
	private String eventType;
	private String accountId;
	private String gatewayOrderId;
	private String gatewayPaymentId;
	private String status;
	private Boolean processed;
	private String processingMessage;
	private String payload;
	private LocalDateTime receivedAt;
}