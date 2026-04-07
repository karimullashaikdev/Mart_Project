package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.DeliveryPricingConfig;

public interface DeliveryPricingConfigRepository extends JpaRepository<DeliveryPricingConfig, UUID> {
	
	 @Query("""
		        SELECT d 
		        FROM DeliveryPricingConfig d 
		        WHERE d.isActive = true
		        AND (d.effectiveFrom IS NULL OR d.effectiveFrom <= :now)
		        AND (d.effectiveTo IS NULL OR d.effectiveTo >= :now)
		        ORDER BY d.effectiveFrom DESC
		    """)
		    List<DeliveryPricingConfig> findActiveConfigs(LocalDateTime now);
	 
	 @Modifying
	    @Transactional
	    @Query("UPDATE DeliveryPricingConfig d SET d.isActive = false, d.updatedBy = :actorId, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id = :id")
	    void deactivateById(@Param("id") UUID id, @Param("actorId") UUID actorId);
	 
	 List<DeliveryPricingConfig> findAll();
}
