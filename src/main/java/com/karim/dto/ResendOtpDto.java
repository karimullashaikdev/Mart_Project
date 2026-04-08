package com.karim.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendOtpDto {
	@NotBlank
	@Email(message = "Valid email is required")
	private String email;
}
