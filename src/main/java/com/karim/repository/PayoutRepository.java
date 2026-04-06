package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    // ✅ findById
    Optional<Payout> findById(UUID id);

    // ✅ findByAgent (with pagination)
    Page<Payout> findByAgentId(UUID agentId, Pageable pageable);

    // ✅ create/update → handled by save()

    // ✅ softDelete
    @Modifying
    @Query("UPDATE Payout p SET p.deleted = true, p.deletedBy = :actorId WHERE p.id = :id")
    void softDelete(@Param("id") UUID id, @Param("actorId") UUID actorId);
}
