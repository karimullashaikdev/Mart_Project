package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.karim.entity.DeliveryAgent;

public interface DeliveryAgentRepository extends JpaRepository<DeliveryAgent, UUID> {

    // ✅ findById
    Optional<DeliveryAgent> findById(UUID id);

    // ✅ findByUserId
    Optional<DeliveryAgent> findByUserId(UUID userId);

    // ✅ findAvailable (status = 'available' AND isVerified = true)
    @Query("SELECT d FROM DeliveryAgent d WHERE d.availabilityStatus = 'AVAILABLE' AND d.isVerified = true")
    List<DeliveryAgent> findAvailable();

    // ✅ create(data) → handled by save()

    // ✅ update(id, data) → handled by save()

    // ❗ softDelete is usually handled in service layer, not repository
    // Example: update status / isDeleted flag instead of physical delete
}
