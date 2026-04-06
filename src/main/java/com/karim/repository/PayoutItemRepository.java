package com.karim.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutItemRepository extends JpaRepository<PayoutItem, UUID> {

    // ✅ bulkCreate → saveAll()

    // ✅ findByPayout
    List<PayoutItem> findByPayoutId(UUID payoutId);

    // ✅ softDelete
    @Modifying
    @Query("UPDATE PayoutItem pi SET pi.deleted = true, pi.deletedBy = :actorId WHERE pi.id = :id")
    void softDelete(@Param("id") UUID id, @Param("actorId") UUID actorId);
}
