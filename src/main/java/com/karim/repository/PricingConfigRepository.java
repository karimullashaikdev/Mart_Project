package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricingConfigRepository extends JpaRepository<PricingConfig, UUID> {

	// ✅ findById
	Optional<PricingConfig> findById(UUID id);

	// ✅ findActive (is_active = true and within effective dates)
	@Query("""
			    SELECT p FROM PricingConfig p
			    WHERE p.isActive = true
			    AND :currentDate BETWEEN p.effectiveFrom AND p.effectiveTo
			""")
	Optional<PricingConfig> findActive(@Param("currentDate") LocalDateTime currentDate);

	// ✅ findAll
	List<PricingConfig> findAll();

	// ✅ create(data) → handled by save()

	// ✅ update(id, data) → handled by save() in service

}
