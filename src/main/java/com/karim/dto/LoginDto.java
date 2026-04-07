package com.karim.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDto {

	@NotBlank
	@Email(message = "Valid email is required")
	private String email;

	@NotBlank(message = "Password is required")
	private String password;
}
