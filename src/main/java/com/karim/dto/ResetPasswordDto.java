package com.karim.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDto {

	@NotBlank
	@Email(message = "Valid email is required")
	private String email;

	@NotBlank
	@Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
	@Pattern(regexp = "^[0-9]{6}$", message = "OTP must be numeric")
	private String otp;

	@NotBlank
	@Size(min = 8, message = "Password must be at least 8 characters")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$", message = "Password must contain uppercase, lowercase, digit and special character")
	private String newPassword;
}
