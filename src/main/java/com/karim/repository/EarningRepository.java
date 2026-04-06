package com.karim.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EarningRepository extends JpaRepository<Earning, UUID> {

    // ✅ findById
    Optional<Earning> findById(UUID id);

    // ✅ findByAgent (with pagination)
    Page<Earning> findByAgentId(UUID agentId, Pageable pageable);

    // Optional: if you need filtering (status/date etc.), you can use Specification or custom queries

    // ✅ findByAssignment
    Optional<Earning> findByAssignmentId(UUID assignmentId);

    // ✅ sumApprovedUnpaid
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Earning e " +
           "WHERE e.agent.id = :agentId AND e.status = 'APPROVED' AND e.paid = false")
    BigDecimal sumApprovedUnpaid(@Param("agentId") UUID agentId);

    // ✅ bulkUpdateStatus
    @Query("UPDATE Earning e SET e.status = :status WHERE e.id IN :ids")
    void bulkUpdateStatus(@Param("ids") List<UUID> ids, @Param("status") String status);

    // ✅ softDelete
    @Query("UPDATE Earning e SET e.deleted = true, e.deletedBy = :actorId WHERE e.id = :id")
    void softDelete(@Param("id") UUID id, @Param("actorId") UUID actorId);
}
