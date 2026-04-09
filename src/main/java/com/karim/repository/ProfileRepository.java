package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.karim.entity.UserProfile;

public interface ProfileRepository extends JpaRepository<UserProfile, UUID> {

    // ✅ Only active profile
    @Query("SELECT p FROM UserProfile p WHERE p.user.id = :userId AND p.isDeleted = false")
    Optional<UserProfile> findActiveByUserId(UUID userId);

    // ✅ Include deleted also
    @Query("SELECT p FROM UserProfile p WHERE p.user.id = :userId")
    Optional<UserProfile> findAnyByUserId(UUID userId);
}
