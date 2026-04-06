package com.karim.service;

import java.util.UUID;

import com.karim.dto.OtpResponseDto;
import com.karim.dto.RegisterDto;
import com.karim.entity.User;

public interface AuthService {
	
	User register(RegisterDto dto);

	OtpResponseDto sendEmailVerificationOtp(UUID userId);
	
	OtpResponseDto verifyEmailOtp(UUID userId, String otp);
	
	OtpResponseDto sendPasswordResetOtp(String email);
	
	void resetPassword(UUID userId, String otp, String newPassword);
	
	void softDeleteUser(UUID userId, UUID actorId);
}
