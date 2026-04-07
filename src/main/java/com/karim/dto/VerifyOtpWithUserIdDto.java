package com.karim.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpWithUserIdDto {
	@NotNull
	private UUID userId;

	@NotBlank
	@Size(min = 6, max = 6)
	private String otp;
}
