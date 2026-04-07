package com.karim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpDto {

	@NotBlank
	@Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
	@Pattern(regexp = "^[0-9]{6}$", message = "OTP must be numeric")
	private String otp;
}