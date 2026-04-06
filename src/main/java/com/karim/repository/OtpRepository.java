package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.Otp;
import com.karim.enums.OtpPurpose;

public interface OtpRepository extends JpaRepository<Otp, UUID>{
	
	Optional<Otp> findTopByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);
}
