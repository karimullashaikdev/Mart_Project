package com.karim.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePricingConfigDto {

	@NotBlank
	private String configName;

	@NotNull
	@Min(0)
	private Float basePrice;

	@NotNull
	@Min(0)
	private Float pricePerKm;

	@NotNull
	@Min(0)
	private Float minDistanceKm;

	@Min(1)
	private Float surgeMultiplier;

	private Boolean isActive;

	private String description;

	private LocalDateTime effectiveFrom;

	private LocalDateTime effectiveTo;
}