package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.DeliveryPricingConfig;

public interface PricingConfigRepository extends JpaRepository<DeliveryPricingConfig, UUID> {

	// ✅ findById
	Optional<DeliveryPricingConfig> findById(UUID id);

	// ✅ findActive (is_active = true and within effective dates)
	@Query("""
		    SELECT p FROM DeliveryPricingConfig p
		    WHERE p.isActive = true
		    AND :currentDate >= p.effectiveFrom
		    AND (p.effectiveTo IS NULL OR :currentDate <= p.effectiveTo)
		""")
		Optional<DeliveryPricingConfig> findActive(@Param("currentDate") LocalDateTime currentDate);

	// ✅ findAll
	List<DeliveryPricingConfig> findAll();

	// ✅ create(data) → handled by save()

	// ✅ update(id, data) → handled by save() in service

}
