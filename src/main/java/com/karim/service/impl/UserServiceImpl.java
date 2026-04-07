package com.karim.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.CreateUserProfileDto;
import com.karim.dto.UpdateProfileDto;
import com.karim.dto.UpdateUserDto;
import com.karim.dto.UserFilterDto;
import com.karim.dto.UserProfileDto;
import com.karim.entity.User;
import com.karim.entity.UserProfile;
import com.karim.exception.DuplicateResourceFoundException;
import com.karim.exception.ResourceNotFoundException;
import com.karim.repository.ProfileRepository;
import com.karim.repository.UserRepository;
import com.karim.service.UserService;
import com.karim.specifications.UserSpecification;

@Service
public class UserServiceImpl implements UserService {

	private final UserRepository userRepo;
	private final ProfileRepository profileRepo;

	private static final SecureRandom random = new SecureRandom();

	public UserServiceImpl(UserRepository userRepo, ProfileRepository profileRepo) {
		this.userRepo = userRepo;
		this.profileRepo = profileRepo;
	}

	@Override
	public User getUser(UUID userId) {
		User user = userRepo.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
		return user;
	}

	@Override
	public User updateUser(UUID userId, UpdateUserDto dto, UUID actorId) {

		User user = userRepo.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

		// ✅ Update only non-null fields
		if (dto.getFullName() != null) {
			user.setFullName(dto.getFullName());
		}

		if (dto.getEmail() != null) {
			user.setEmail(dto.getEmail());
		}

		if (dto.getPhone() != null) {
			user.setPhone(dto.getPhone());
		}

		if (dto.getIsActive() != null) {
			user.setIsActive(dto.getIsActive());
		}

		// Optional: track who updated
		user.setUpdatedBy(actorId);

		return userRepo.save(user);
	}

	@Override
	public UserProfileDto getProfile(UUID userId) {
		UserProfile userProfile = profileRepo.findByUserId(userId).orElseThrow(
				() -> new ResourceNotFoundException("User Profile not found with given user id " + userId));
		return mapToDto(userProfile);
	}

	@Override
	@Transactional
	public UserProfile createProfile(UUID userId, CreateUserProfileDto dto, UUID actorId) {

		// 1. Validate user exists
		User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		// 2. Check if profile already exists
		profileRepo.findByUserId(userId).ifPresent(p -> {
			throw new DuplicateResourceFoundException("Profile already exists for this user");
		});

		// 3. Create profile
		UserProfile profile = new UserProfile();
		profile.setUser(user);
		profile.setAvatarUrl(dto.getAvatarUrl());
		profile.setDateOfBirth(dto.getDateOfBirth());
		profile.setGender(dto.getGender());
		// profile.setFcmToken(dto.getFcmToken());

		// 4. Audit fields
		profile.setCreatedBy(actorId);
		profile.setUpdatedBy(actorId);

		// createdAt, updatedAt handled by @PrePersist

		// 5. Save
		return profileRepo.save(profile);
	}

	private UserProfileDto mapToDto(UserProfile profile) {
		User user = profile.getUser();

		UserProfileDto dto = new UserProfileDto();

		dto.setUserId(user.getId());
		dto.setFullName(user.getFullName());
		dto.setEmail(user.getEmail());
		dto.setPhone(user.getPhone());
		dto.setRole(user.getRole());
		dto.setIsActive(user.getIsActive());

		dto.setAvatarUrl(profile.getAvatarUrl());
		dto.setDateOfBirth(profile.getDateOfBirth());
		dto.setGender(profile.getGender());

		return dto;
	}

	@Override
	@Transactional
	public UserProfile updateProfile(UUID userId, UpdateProfileDto dto, UUID actorId) {

		// 1. Fetch existing profile
		UserProfile profile = profileRepo.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User profile not found"));

		// 2. Update only non-null fields (partial update)

		if (dto.getAvatarUrl() != null) {
			profile.setAvatarUrl(dto.getAvatarUrl());
		}

		if (dto.getDateOfBirth() != null) {
			profile.setDateOfBirth(dto.getDateOfBirth());
		}

		if (dto.getGender() != null) {
			profile.setGender(dto.getGender());
		}

//	    if (dto.getFcmToken() != null) {
//	        profile.setFcmToken(dto.getFcmToken());
//	    }

//	    if (dto.getDeviceType() != null) {
//	        profile.setDeviceType(dto.getDeviceType());
//	    }

		// 3. Audit fields
		profile.setUpdatedBy(actorId);

		// updatedAt handled by @PreUpdate

		// 4. Save updated entity
		return profileRepo.save(profile);
	}

	@Override
	@Transactional
	public void softDeleteProfile(UUID userId, UUID actorId) {

		// 1. Fetch profile
		UserProfile profile = profileRepo.findByUserId(userId)
				.orElseThrow(() -> new RuntimeException("User profile not found"));

		// 2. Set audit fields BEFORE delete
		profile.setDeletedBy(actorId);
		profile.setDeletedAt(LocalDateTime.now());

		// 3. Soft delete (Hibernate will update is_deleted = true)
		profileRepo.delete(profile);
	}

	@Override
	public Page<User> listUsers(UserFilterDto filters, Pageable pageable) {

		Specification<User> spec = UserSpecification.filter(filters);

		return userRepo.findAll(spec, pageable);
	}

	@Override
	@Transactional
	public void restoreUser(UUID userId, UUID actorId) {

		// Optional: check if user exists (including deleted ones)
		// For this, you may need a separate repository method without @SQLRestriction

		int updated = userRepo.restoreUser(userId);

		if (updated == 0) {
			throw new RuntimeException("User not found or already active");
		}
	}
}
