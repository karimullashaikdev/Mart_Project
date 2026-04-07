package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.DeliveryRide;

public interface DeliveryRideRepository extends JpaRepository<DeliveryRide, UUID> {

    // ✅ findById
    Optional<DeliveryRide> findById(UUID id);

    // ✅ findByAssignment
    Optional<DeliveryRide> findByAssignmentId(UUID assignmentId);

    // ✅ create(data) → handled by save()

    // ✅ update(id, data) → handled by save() in service

    // ✅ softDelete(id, actorId)
    @Modifying
    @Transactional
    @Query("""
        UPDATE DeliveryRide d
        SET d.isDeleted = true,
            d.deletedBy = :actorId,
            d.deletedAt = CURRENT_TIMESTAMP
        WHERE d.id = :id
    """)
    void softDelete(@Param("id") UUID id,
                    @Param("actorId") UUID actorId);
}
