package com.karim.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.OtpResponseDto;
import com.karim.dto.RegisterDto;
import com.karim.entity.Otp;
import com.karim.entity.User;
import com.karim.enums.OtpPurpose;
import com.karim.enums.Role;
import com.karim.exception.ResourceNotFoundException;
import com.karim.repository.OtpRepository;
import com.karim.repository.UserRepository;
import com.karim.service.AuthService;
import com.karim.service.NotificationService;

public class AuthServiceImpl implements AuthService {

	private final UserRepository userRepo;
	private final PasswordEncoder passwordEncoder;
	private final OtpRepository otpRepo;
	private final NotificationService notificationService;

	private static final SecureRandom random = new SecureRandom();

	public AuthServiceImpl(UserRepository userRepo, PasswordEncoder passwordEncoder,OtpRepository otpRepo, NotificationService notificationService) {
		this.userRepo = userRepo;
		this.passwordEncoder = passwordEncoder;
		this.otpRepo=otpRepo;
		this.notificationService=notificationService;
	}

	// -------------------------
	// REGISTER USER
	// -------------------------
	@Override
	@Transactional
	public User register(RegisterDto dto) {

		// 1. Check duplicates
		if (userRepo.existsByEmail(dto.getEmail())) {
			throw new RuntimeException("Email already exists");
		}

		if (userRepo.existsByPhone(dto.getPhone())) {
			throw new RuntimeException("Phone already exists");
		}

		// 2. Create user
		User user = new User();
		user.setFullName(dto.getFullName());
		user.setEmail(dto.getEmail());
		user.setPhone(dto.getPhone());

		// hash password
		user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

		// role logic
		if (dto.getRole() != null) {
			user.setRole(dto.getRole());
		} else {
			user.setRole(Role.USER);
		}

		// default states
		user.setIsActive(false); // inactive until verification

		// audit (optional)
		user.setCreatedAt(LocalDateTime.now());

		User savedUser = userRepo.save(user);

		// 3. Trigger OTP
		sendEmailVerificationOtp(savedUser.getId());

		return savedUser;
	}

	// -------------------------
	// SEND EMAIL OTP
	// -------------------------
	@Override
	@Transactional
	public OtpResponseDto sendEmailVerificationOtp(UUID userId) {

		// 1. Validate user
		User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		// 2. Generate OTP (6-digit)
		String otp = String.format("%06d", random.nextInt(999999));

		// 3. Hash OTP
		String otpHash = passwordEncoder.encode(otp);

		// 4. Build OTP entity
		Otp otpEntity = new Otp();
		otpEntity.setUserId(userId);
		otpEntity.setReferenceId(UUID.randomUUID().toString());
		otpEntity.setPurpose(OtpPurpose.REGISTRATION);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpEntity.setMaxAttempts(3);

		// 5. Save OTP
		otpRepo.save(otpEntity);

		// 6. Send OTP via notification service
		notificationService.sendEmail(user.getEmail(), "Your OTP Code", "Your OTP is: " + otp);

		return OtpResponseDto.builder().message("OTP sent successfully").referenceId(otpEntity.getReferenceId())
				.build();
	}

	@Override
	@Transactional
	public OtpResponseDto verifyEmailOtp(UUID userId, String otp) {

		// 1. Fetch user
		User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		// 2. Fetch latest OTP for REGISTRATION
		Otp otpEntity = otpRepo.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, OtpPurpose.REGISTRATION)
				.orElseThrow(() -> new RuntimeException("OTP not found"));

		// 3. Check if already used
		if (otpEntity.isUsed()) {
			throw new RuntimeException("OTP already used");
		}

		// 4. Check expiry
		if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
			otpEntity.setExpired(true);
			throw new RuntimeException("OTP expired");
		}

		// 5. Check attempts
		if (otpEntity.getAttempts() >= otpEntity.getMaxAttempts()) {
			otpEntity.setExpired(true);
			throw new RuntimeException("Max OTP attempts exceeded");
		}

		// 6. Verify OTP
		boolean matches = passwordEncoder.matches(otp, otpEntity.getOtpHash());

		if (!matches) {
			otpEntity.setAttempts(otpEntity.getAttempts() + 1);
			otpRepo.save(otpEntity);
			throw new RuntimeException("Invalid OTP");
		}

		// 7. Mark OTP as used
		otpEntity.setUsed(true);
		otpEntity.setUsedAt(LocalDateTime.now());
		otpRepo.save(otpEntity);

		// 8. Activate user
		user.setIsActive(true);
		user.setUpdatedAt(LocalDateTime.now());
		userRepo.save(user);

		return OtpResponseDto.builder().message("Email verified successfully").referenceId(otpEntity.getReferenceId())
				.build();
	}

	@Override
	@Transactional
	public OtpResponseDto sendPasswordResetOtp(String email) {

		// 1. Validate user
		User user = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

		// 2. Generate OTP (6-digit)
		String otp = String.format("%06d", random.nextInt(999999));

		// 3. Hash OTP
		String otpHash = passwordEncoder.encode(otp);

		// 4. Create OTP entity
		Otp otpEntity = new Otp();
		otpEntity.setUserId(user.getId());
		otpEntity.setReferenceId(UUID.randomUUID().toString());
		otpEntity.setPurpose(OtpPurpose.PASSWORD_RESET);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpEntity.setMaxAttempts(3);

		// 5. Save OTP
		otpRepo.save(otpEntity);

		// 6. Send OTP via email
		notificationService.sendEmail(user.getEmail(), "Password Reset OTP", "Your OTP for password reset is: " + otp);

		return OtpResponseDto.builder().message("Password reset OTP sent successfully")
				.referenceId(otpEntity.getReferenceId()).build();
	}

	@Override
	@Transactional
	public void resetPassword(UUID userId, String otp, String newPassword) {

		// 1. Validate user
		User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		// 2. Fetch latest OTP for PASSWORD_RESET
		Otp otpEntity = otpRepo.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, OtpPurpose.PASSWORD_RESET)
				.orElseThrow(() -> new RuntimeException("OTP not found"));

		// 3. Check if OTP already used
		if (otpEntity.isUsed()) {
			throw new RuntimeException("OTP already used");
		}

		// 4. Check expiry
		if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
			otpEntity.setExpired(true);
			throw new RuntimeException("OTP expired");
		}

		// 5. Check attempts
		if (otpEntity.getAttempts() >= otpEntity.getMaxAttempts()) {
			otpEntity.setExpired(true);
			throw new RuntimeException("Max OTP attempts exceeded");
		}

		// 6. Verify OTP
		boolean matches = passwordEncoder.matches(otp, otpEntity.getOtpHash());

		if (!matches) {
			otpEntity.setAttempts(otpEntity.getAttempts() + 1);
			otpRepo.save(otpEntity);
			throw new RuntimeException("Invalid OTP");
		}

		// 7. Mark OTP as used
		otpEntity.setUsed(true);
		otpEntity.setUsedAt(LocalDateTime.now());
		otpRepo.save(otpEntity);

		// 8. Update password
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		user.setUpdatedAt(LocalDateTime.now());

		userRepo.save(user);
	}

	@Override
	@Transactional
	public void softDeleteUser(UUID userId, UUID actorId) {

		// 1. Fetch user (only non-deleted due to @SQLRestriction)
		User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		// 2. Set audit fields
		user.setDeletedBy(actorId);
		user.setDeletedAt(LocalDateTime.now());

		// 3. Soft delete (triggers @SQLDelete)
		userRepo.delete(user);
	}
}
