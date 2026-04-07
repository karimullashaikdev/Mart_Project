package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryAssignmentResponse {

	private UUID id;
	private UUID orderId;
	private UUID agentId;
	private String status;
	private int attemptNumber;

	private String deliveryProofUrl;
	private String failureReason;

	private UUID assignedBy;

	private LocalDateTime assignedAt;
	private LocalDateTime acceptedAt;
	private LocalDateTime rejectedAt;
	private LocalDateTime pickedUpAt;
	private LocalDateTime deliveredAt;
}
