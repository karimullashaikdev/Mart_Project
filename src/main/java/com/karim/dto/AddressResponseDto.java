package com.karim.dto;

import java.util.UUID;

import com.karim.enums.AddressLabel;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressResponseDto {

	private UUID id;
	private AddressLabel label;
	private String line1;
	private String line2;
	private String city;
	private String state;
	private String pincode;
	private String phone;
	private String landmark;
	private Double latitude;
	private Double longitude;
	private Boolean isDefault;
}