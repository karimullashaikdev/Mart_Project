package com.karim.dto;

import java.time.LocalDate;

import com.karim.enums.Gender;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserProfileDto {

	private String avatarUrl;

	private LocalDate dateOfBirth;

	@NotNull
	private Gender gender;

	// Optional if you plan to support push notifications later
	private String fcmToken;
}
