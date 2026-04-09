package com.karim.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.LoginResponseDto;
import com.karim.dto.OtpResponseDto;
import com.karim.dto.RegisterDto;
import com.karim.entity.Otp;
import com.karim.entity.User;
import com.karim.enums.EmailType;
import com.karim.enums.OtpPurpose;
import com.karim.enums.Role;
import com.karim.exception.ResourceNotFoundException;
import com.karim.repository.OtpRepository;
import com.karim.repository.UserRepository;
import com.karim.service.AuthService;
import com.karim.service.NotificationService;
import com.karim.util.JwtUtil;

@Service
public class AuthServiceImpl implements AuthService {

	@Value("${app.base-url}")
	private String baseUrl;

	private final UserRepository userRepo;
	private final PasswordEncoder passwordEncoder;
	private final OtpRepository otpRepo;
	private final NotificationService notificationService;
	private final JwtUtil jwtUtil;
	private final AuthenticationManager authenticationManager;

	private static final SecureRandom random = new SecureRandom();

	public AuthServiceImpl(UserRepository userRepo, PasswordEncoder passwordEncoder, OtpRepository otpRepo,
			NotificationService notificationService, JwtUtil jwtUtil, AuthenticationManager authenticationManager) {
		this.userRepo = userRepo;
		this.passwordEncoder = passwordEncoder;
		this.otpRepo = otpRepo;
		this.notificationService = notificationService;
		this.jwtUtil = jwtUtil;
		this.authenticationManager = authenticationManager;
	}

	@Override
	@Transactional
	public User register(RegisterDto dto) {

		if (userRepo.existsByEmail(dto.getEmail())) {
			throw new RuntimeException("Email already exists");
		}

		if (userRepo.existsByPhone(dto.getPhone())) {
			throw new RuntimeException("Phone already exists");
		}

		User user = new User();
		user.setFullName(dto.getFullName());
		user.setEmail(dto.getEmail());
		user.setPhone(dto.getPhone());
		user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
		user.setRole(dto.getRole() != null ? dto.getRole() : Role.USER);
		user.setIsActive(false);
		user.setCreatedAt(LocalDateTime.now());

		User savedUser = userRepo.save(user);

		// ✅ set audit
		savedUser.setCreatedBy(savedUser.getId());

		// ✅ send OTP using EMAIL (updated flow)
		sendEmailVerificationOtp(savedUser.getEmail());

		return savedUser;
	}

	// -------------------------
	// LOGIN
	// -------------------------
	@Override
	@Transactional
	public LoginResponseDto login(String email, String password) {

		// 1. Let Spring Security verify credentials (throws on bad creds)
		authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));

		// 2. Load user for token generation
		User user = userRepo.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		// 3. Block inactive users (not yet email-verified)
		if (!user.getIsActive()) {
			throw new RuntimeException("Account is not active. Please verify your email first.");
		}

		// 4. Update login metadata
		user.setLastLoginAt(LocalDateTime.now());
		userRepo.save(user);

		// 5. Generate tokens
		String role = user.getRole().name();
		String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), role);
		String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail(), role);

		return LoginResponseDto.builder().accessToken(accessToken).refreshToken(refreshToken).userId(user.getId())
				.email(user.getEmail()).role(role).build();
	}

	// -------------------------
	// LOGOUT
	// -------------------------
	@Override
	public void logout(UUID userId) {
		/*
		 * With stateless JWT there is nothing to invalidate server-side unless you
		 * maintain a token blacklist / refresh-token store in DB.
		 *
		 * Production approach: - Store the refresh token jti in DB on login - Delete /
		 * mark it revoked here - JwtAuthFilter checks the blacklist on every request
		 *
		 * For now we simply validate the user exists and return. Add blacklist logic
		 * here when you implement the token store.
		 */
		userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
		// TODO: delete refresh token from token store when implemented
	}

	// -------------------------
	// REFRESH TOKEN
	// -------------------------
	@Override
	@Transactional
	public LoginResponseDto refreshToken(String refreshToken) {

		// 1. Validate it is a refresh token (not an access token)
		if (!jwtUtil.isTokenValid(refreshToken, "refresh")) {
			throw new RuntimeException("Invalid or expired refresh token");
		}

		// 2. Extract claims
		UUID userId = jwtUtil.extractUserId(refreshToken);
		String email = jwtUtil.extractEmail(refreshToken);
		String role = jwtUtil.extractRole(refreshToken);

		// 3. Confirm user still exists and is active
		User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		if (!user.getIsActive()) {
			throw new RuntimeException("Account is inactive");
		}

		// 4. Issue new pair
		String newAccessToken = jwtUtil.generateAccessToken(userId, email, role);
		String newRefreshToken = jwtUtil.generateRefreshToken(userId, email, role);

		return LoginResponseDto.builder().accessToken(newAccessToken).refreshToken(newRefreshToken).userId(userId)
				.email(email).role(role).build();
	}

	@Override
	@Transactional
	public OtpResponseDto sendEmailVerificationOtp(String email) {

		// ✅ 1. Find user by email
		User user = userRepo.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		// ✅ 2. Generate OTP
		String otp = String.format("%06d", random.nextInt(1_000_000));
		String otpHash = passwordEncoder.encode(otp);

		// ✅ 3. Save OTP
		Otp otpEntity = new Otp();
		otpEntity.setUserId(user.getId());
		otpEntity.setReferenceId(UUID.randomUUID().toString());
		otpEntity.setPurpose(OtpPurpose.REGISTRATION);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpEntity.setMaxAttempts(3);

		otpRepo.save(otpEntity);

		String subject = "Welcome to Our App 🎉 - Verify Your Account";

		String body = htmlBody(otp, user.getFullName(), user.getId());

		// ✅ 4. Send Email
		notificationService.sendEmail(user.getId(), EmailType.WELCOME, user.getEmail(), subject, body,
				otpEntity.getReferenceId());

		// ✅ 5. Response
		return OtpResponseDto.builder().message("OTP sent successfully").referenceId(otpEntity.getReferenceId())
				.build();
	}

	// -------------------------
	// VERIFY EMAIL OTP
	// -------------------------
	@Override
	@Transactional
	public OtpResponseDto verifyEmailOtp(UUID userId, String otp) {

		User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		Otp otpEntity = otpRepo.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, OtpPurpose.REGISTRATION)
				.orElseThrow(() -> new ResourceNotFoundException("OTP not found"));

		validateOtp(otpEntity, otp);

		otpEntity.setUsed(true);
		otpEntity.setUsedAt(LocalDateTime.now());
		otpRepo.save(otpEntity);

		user.setIsActive(true);
		user.setUpdatedAt(LocalDateTime.now());
		userRepo.save(user);

		return OtpResponseDto.builder().message("Email verified successfully").referenceId(otpEntity.getReferenceId())
				.build();
	}

	@Override
	@Transactional
	public OtpResponseDto sendPasswordResetOtp(String email) {

		User user = userRepo.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		String otp = String.format("%06d", random.nextInt(1_000_000));
		String otpHash = passwordEncoder.encode(otp);

		Otp otpEntity = new Otp();
		otpEntity.setUserId(user.getId());
		otpEntity.setReferenceId(UUID.randomUUID().toString());
		otpEntity.setPurpose(OtpPurpose.PASSWORD_RESET);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpEntity.setMaxAttempts(3);
		otpRepo.save(otpEntity);

		// Updated to use EmailType enum instead of "otp" string
		notificationService.sendEmail(user.getId(), EmailType.PASSWORD_RESET, // <-- Use enum instead of string
				user.getEmail(), "Password Reset OTP", "Your OTP for password reset is: " + otp,
				otpEntity.getReferenceId());

		return OtpResponseDto.builder().message("Password reset OTP sent successfully")
				.referenceId(otpEntity.getReferenceId()).build();
	}

	// -------------------------
	// RESET PASSWORD
	// -------------------------
	@Override
	@Transactional
	public void resetPassword(UUID userId, String otp, String newPassword) {

		User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		Otp otpEntity = otpRepo.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, OtpPurpose.PASSWORD_RESET)
				.orElseThrow(() -> new ResourceNotFoundException("OTP not found"));

		validateOtp(otpEntity, otp);

		otpEntity.setUsed(true);
		otpEntity.setUsedAt(LocalDateTime.now());
		otpRepo.save(otpEntity);

		user.setPasswordHash(passwordEncoder.encode(newPassword));
		user.setUpdatedAt(LocalDateTime.now());
		userRepo.save(user);
	}

	// -------------------------
	// SOFT DELETE USER
	// -------------------------
	@Override
	@Transactional
	public void softDeleteUser(UUID userId, UUID actorId) {

		User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		user.setDeletedBy(actorId);
		user.setDeletedAt(LocalDateTime.now());
		userRepo.delete(user); // @SQLDelete on entity runs UPDATE instead of DELETE
	}

	// ----------------------------------------------------------------
	// PRIVATE HELPER — shared OTP validation logic
	// Throws on: already used, expired, max attempts exceeded, wrong OTP
	// Saves the entity when state changes (expired flag, attempt count)
	// ----------------------------------------------------------------
	private void validateOtp(Otp otpEntity, String rawOtp) {

		if (otpEntity.isUsed()) {
			throw new RuntimeException("OTP already used");
		}

		if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
			otpEntity.setExpired(true);
			otpRepo.save(otpEntity);
			throw new RuntimeException("OTP expired");
		}

		if (otpEntity.getAttempts() >= otpEntity.getMaxAttempts()) {
			otpEntity.setExpired(true);
			otpRepo.save(otpEntity);
			throw new RuntimeException("Max OTP attempts exceeded");
		}

		if (!passwordEncoder.matches(rawOtp, otpEntity.getOtpHash())) {
			otpEntity.setAttempts(otpEntity.getAttempts() + 1);
			otpRepo.save(otpEntity);
			throw new RuntimeException("Invalid OTP");
		}
	}

	private String htmlBody(String otp, String fullname, UUID userId) {
		String activationLink = baseUrl + "/activate.html";
		String htmlBody = "<html>" + "<body style='font-family: Arial, sans-serif; line-height:1.6;'>"
				+ "<h2 style='color:#2E86C1;'>Welcome to Our App 🎉</h2>" +

				"<p>Dear <b>" + fullname + "</b>,</p>" +

				"<p>Thank you for registering with us. We are excited to have you on board!</p>" +

				"<p>To complete your registration and activate your account, please use the UserId, OTP below:</p>" +

				"<h1 style='color:#28A745;'>" + userId + "</h1>" + "<h1 style='color:#28A745;'>" + otp + "</h1>" +

				"<p><b>Important:</b></p>" + "<ul>" + "<li>This OTP is valid for 5 minutes only.</li>"
				+ "<li>You must verify your account before accessing features like ordering products.</li>"
				+ "<li>Inactive accounts cannot place orders.</li>" + "</ul>" +

				"<p>Please click below to activate your account:</p>" +

				"<p>" + "<a href='" + activationLink
				+ "' style='background-color:#2E86C1;color:white;padding:10px 15px;text-decoration:none;border-radius:5px;'>Activate Account</a>"
				+ "</p>" +

				"<br/>" + "<p>Regards,<br/><b>Your App Team</b></p>" + "</body>" + "</html>";

		return htmlBody;
	}
}