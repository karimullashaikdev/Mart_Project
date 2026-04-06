package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.UserProfile;

public interface ProfileRepository extends JpaRepository<UserProfile, UUID> {

	// ✅ findByUserId
	Optional<UserProfile> findByUserId(UUID userId);
}
