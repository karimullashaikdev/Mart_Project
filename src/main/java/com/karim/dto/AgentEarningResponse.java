package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.karim.enums.EarningStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentEarningResponse {

	private UUID id;
	private UUID rideId;
	private UUID assignmentId;
	private UUID agentId;

	private float baseEarning;
	private float kmEarning;
	private float surgeEarning;
	private float netEarning;

	private EarningStatus status;

	private LocalDateTime createdAt;
}