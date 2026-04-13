package com.karim.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAddressDto {

	private String label;

	@NotBlank
	private String line1;

	private String line2;

	@NotBlank
	private String city;

	@NotBlank
	private String state;

	@NotBlank
	private String pincode;

	private String phone;

	private String landmark;

	private Double latitude;

	private Double longitude;

	private Boolean isDefault;
}