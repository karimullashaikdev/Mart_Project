package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayoutResponse {

	private UUID id;

	private UUID agentId;

	private Float amount;

	private String status;

	private String payoutReference; // optional: txn/reference id from payment provider

	private LocalDateTime createdAt;

	private LocalDateTime processedAt;

	private LocalDateTime paidAt;

}