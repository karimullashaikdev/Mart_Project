package com.karim.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karim.dto.ForgotPasswordDto;
import com.karim.dto.LoginDto;
import com.karim.dto.LoginResponseDto;
import com.karim.dto.OtpResponseDto;
import com.karim.dto.RefreshTokenDto;
import com.karim.dto.RegisterDto;
import com.karim.dto.ResendOtpDto;
import com.karim.dto.ResetPasswordDto;
import com.karim.dto.VerifyOtpDto;
import com.karim.entity.User;
import com.karim.repository.UserRepository;
import com.karim.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * AuthController
 *
 * Base route: /api/auth
 *
 * ┌─────────────────────────────────────────────────────────────────┐ │ Method
 * Route Guard Calls │
 * ├─────────────────────────────────────────────────────────────────┤ │ POST
 * /register Public authService.register │ │ POST /login Public
 * authService.login │ │ POST /logout JWT authService.logout │ │ POST
 * /refresh-token Public authService.refresh │ │ POST /otp/email/send JWT
 * sendEmailOtp │ │ POST /otp/email/verify JWT verifyEmailOtp │ │ POST
 * /forgot-password Public sendPasswordReset │ │ POST /reset-password Public
 * resetPassword │ │ DELETE /account JWT softDeleteUser │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Controller rules followed: - No business logic here — only HTTP parsing,
 * validation, delegation - @AuthenticationPrincipal injects the logged-in
 * UserDetails - actorId is always the currently authenticated user's ID - All
 * responses follow the shape: { success, data/error, message }
 */
@Tag(name = "Auth", description = "Registration, login, OTP verification, password reset")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final UserRepository userRepo; // only to resolve email→UUID for reset-password

	public AuthController(AuthService authService, UserRepository userRepo) {
		this.authService = authService;
		this.userRepo = userRepo;
	}

	// ----------------------------------------------------------------
	// POST /api/auth/register — PUBLIC
	// ----------------------------------------------------------------
	@Operation(summary = "Register a new user", description = "Creates account with role=CLIENT by default. Sends email OTP immediately.")
	@SecurityRequirements // empty = no auth needed — overrides global bearerAuth
	@PostMapping("/register")
	public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterDto dto) {

		User savedUser = authService.register(dto);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(Map.of("success", true, "message", "Registration successful. Check your email for the OTP.",
						"data", Map.of("userId", savedUser.getId(), "email", savedUser.getEmail())));
	}

	// ----------------------------------------------------------------
	// POST /api/auth/login — PUBLIC
	// ----------------------------------------------------------------
	@Operation(summary = "Login", description = "Returns accessToken (15 min) and refreshToken (7 days). Use accessToken in Authorize above.")
	@SecurityRequirements
	@PostMapping("/login")
	public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginDto dto) {

		// AuthService.login() authenticates, updates last_login_ip/at, returns tokens
		LoginResponseDto tokens = authService.login(dto.getEmail(), dto.getPassword());

		return ResponseEntity.ok(Map.of("success", true, "message", "Login successful", "data", tokens));
	}

	// ----------------------------------------------------------------
	// POST /api/auth/logout — JWT required
	// ----------------------------------------------------------------
	@Operation(summary = "Logout", description = "Invalidates the current session. JWT required.")
	@PostMapping("/logout")
	public ResponseEntity<Map<String, Object>> logout(@AuthenticationPrincipal UserDetails currentUser) {

		UUID userId = resolveUserId(currentUser);
		authService.logout(userId);

		return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
	}

	// ----------------------------------------------------------------
	// POST /api/auth/refresh-token — PUBLIC
	// ----------------------------------------------------------------
	@Operation(summary = "Refresh access token", description = "Pass the refreshToken from login to get a new access token pair.")
	@SecurityRequirements
	@PostMapping("/refresh-token")
	public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshTokenDto dto) {

		LoginResponseDto tokens = authService.refreshToken(dto.getRefreshToken());

		return ResponseEntity.ok(Map.of("success", true, "message", "Token refreshed", "data", tokens));
	}

	// ----------------------------------------------------------------
	// POST /api/auth/otp/email/send — JWT required
	// ----------------------------------------------------------------
	@Operation(
	    summary = "Resend email verification OTP",
	    description = "Sends a fresh 6-digit OTP to the given email."
	)
	@PostMapping("/otp/email/send")
	public ResponseEntity<Map<String, Object>> sendEmailOtp(
	        @Valid @RequestBody ResendOtpDto dto) {

	    OtpResponseDto result =
	            authService.sendEmailVerificationOtp(dto.getEmail());

	    return ResponseEntity.ok(Map.of(
	            "success", true,
	            "message", result.getMessage(),
	            "data", Map.of("referenceId", result.getReferenceId())
	    ));
	}

	// ----------------------------------------------------------------
	// POST /api/auth/otp/email/verify — JWT required
	// ----------------------------------------------------------------
	@Operation(summary = "Verify email OTP", description = "Submit the 6-digit OTP to activate the account. JWT required.")
	@PostMapping("/otp/email/verify")
	public ResponseEntity<Map<String, Object>> verifyEmailOtp(/* @AuthenticationPrincipal UserDetails currentUser, */
			@Valid @RequestBody VerifyOtpDto dto) {

		/* UUID userId = resolveUserId(currentUser); */
		OtpResponseDto result = authService.verifyEmailOtp(dto.getUserId(), dto.getOtp());

		return ResponseEntity.ok(Map.of("success", true, "message", result.getMessage(), "data",
				Map.of("referenceId", result.getReferenceId())));
	}

	// ----------------------------------------------------------------
	// POST /api/auth/forgot-password — PUBLIC
	// ----------------------------------------------------------------
	@Operation(summary = "Forgot password — send OTP", description = "Step 1 of password reset. Sends a 6-digit OTP to the provided email.")
	@SecurityRequirements
	@PostMapping("/forgot-password")
	public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordDto dto) {

		OtpResponseDto result = authService.sendPasswordResetOtp(dto.getEmail());

		return ResponseEntity.ok(Map.of("success", true, "message", result.getMessage(), "data",
				Map.of("referenceId", result.getReferenceId())));
	}

	// ----------------------------------------------------------------
	// POST /api/auth/reset-password — PUBLIC
	// ----------------------------------------------------------------
	@Operation(summary = "Reset password", description = "Step 2 of password reset. Provide email + OTP from forgot-password + new password.")
	@SecurityRequirements
	@PostMapping("/reset-password")
	public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordDto dto) {

		// Resolve userId from email (needed by authService.resetPassword)
		User user = userRepo.findByEmail(dto.getEmail())
				.orElseThrow(() -> new com.karim.exception.ResourceNotFoundException("No user found with that email"));

		authService.resetPassword(user.getId(), dto.getOtp(), dto.getNewPassword());

		return ResponseEntity
				.ok(Map.of("success", true, "message", "Password reset successfully. You can now log in."));
	}

	// ----------------------------------------------------------------
	// DELETE /api/auth/account — JWT required
	// ----------------------------------------------------------------
	@Operation(summary = "Delete own account", description = "Soft-deletes the currently authenticated user's account. JWT required.")
	@DeleteMapping("/account")
	public ResponseEntity<Map<String, Object>> deleteAccount(@AuthenticationPrincipal UserDetails currentUser) {

		UUID userId = resolveUserId(currentUser);
		authService.softDeleteUser(userId, userId); // actor = self

		return ResponseEntity.ok(Map.of("success", true, "message", "Account deleted successfully"));
	}

	// ----------------------------------------------------------------
	// HELPER — resolve UUID from Spring Security UserDetails
	// UserDetails.getUsername() returns the email (set in CustomUserDetailsService)
	// We do ONE DB call to get the UUID.
	// ----------------------------------------------------------------
	private UUID resolveUserId(UserDetails currentUser) {
		return userRepo.findByEmail(currentUser.getUsername())
				.orElseThrow(() -> new com.karim.exception.ResourceNotFoundException("Authenticated user not found"))
				.getId();
	}
}