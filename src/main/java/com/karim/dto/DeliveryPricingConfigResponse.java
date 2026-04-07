package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryPricingConfigResponse {

	private UUID id;
	private String configName;
	private Float basePrice;
	private Float pricePerKm;
	private Float minDistanceKm;
	private Float surgeMultiplier;
	private Boolean isActive;
	private String description;
	private LocalDateTime effectiveFrom;
	private LocalDateTime effectiveTo;
}