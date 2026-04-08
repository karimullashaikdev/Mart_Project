package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressResponseDto {

	private UUID id;

	private String label;
	private String line1;
	private String line2;
	private String city;
	private String state;
	private String pincode;

	private Double latitude;
	private Double longitude;

	private Boolean isDefault;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}