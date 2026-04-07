package com.karim.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryAgentResponse {

	private UUID id;
	private UUID userId;
	private String vehicleType;
	private String vehicleNumber;
	private String licenseNumber;
	private String availabilityStatus;
	private boolean isVerified;
	private float ratingAvg;
	private int totalDeliveries;
	private float totalEarningsAllTime;
	private float walletBalance;
}