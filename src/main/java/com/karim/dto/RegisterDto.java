package com.karim.dto;

import com.karim.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDto {

	@NotBlank
	private String fullName;

	@Email
	@NotBlank
	private String email;

	@NotBlank
	private String phone;

	@NotBlank
	@Size(min = 8)
	@Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$", message = "Password must contain upper, lower case letters and a digit")
	private String password;

	private Role role; // optional (admin-only)
}