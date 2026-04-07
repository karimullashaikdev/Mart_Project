package com.karim.dto;

import com.karim.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDto {

	@NotBlank(message = "Full name is required")
	private String fullName;

	@NotBlank
	@Email(message = "Valid email is required")
	private String email;

	@NotBlank
	@Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Valid phone number is required")
	private String phone;

	@NotBlank
	@Size(min = 8, message = "Password must be at least 8 characters")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$", message = "Password must contain uppercase, lowercase, digit and special character")
	private String password;

	// Only admins should be allowed to set this — enforce in controller/service
	private Role role;
}