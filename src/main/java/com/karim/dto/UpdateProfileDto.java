package com.karim.dto;

import java.time.LocalDate;

import com.karim.enums.Gender;

import lombok.Data;

@Data
public class UpdateProfileDto {

	private String avatarUrl;
	
	private String avatarPublicId;

	private LocalDate dateOfBirth;

	private Gender gender;

	//private String fcmToken;

	//private String deviceType;
}