package com.karim.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.karim.enums.Gender;
import com.karim.enums.Role;

import lombok.Data;

@Data
public class UserProfileDto {

	private UUID userId;

	private String fullName;
	private String email;
	private String phone;
	private Role role;
	private Boolean isActive;

	private String avatarUrl;
	private LocalDate dateOfBirth;
	private Gender gender;
}
