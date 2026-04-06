package com.karim.dto;

import com.karim.enums.AddressLabel;

import lombok.Data;

@Data
public class UpdateAddressDto {

	private AddressLabel label;

	private String line1;

	private String line2;

	private String city;

	private String state;

	private String pincode;

	private Double latitude;

	private Double longitude;

	private Boolean isDefault;
}
