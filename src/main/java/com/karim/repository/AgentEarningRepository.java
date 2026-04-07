package com.karim.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.AgentEarning;

public interface AgentEarningRepository extends JpaRepository<AgentEarning, UUID> {
	Optional<AgentEarning> findByRideId(UUID rideId);

	@Query("""
			SELECT e FROM AgentEarning e
			WHERE e.agentId = :agentId
			AND (:status IS NULL OR e.status = :status)
			AND (:fromDate IS NULL OR e.createdAt >= :fromDate)
			AND (:toDate IS NULL OR e.createdAt <= :toDate)
			""")
	Page<AgentEarning> findByFilters(@Param("agentId") UUID agentId, @Param("status") String status,
			@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate, Pageable pageable);

	@Query("SELECT COALESCE(SUM(e.netEarning), 0) FROM AgentEarning e "
			+ "WHERE e.agentId = :agentId AND e.status = 'PENDING'")
	BigDecimal sumPendingEarningsByAgentId(@Param("agentId") UUID agentId);

	// ✅ findById
	Optional<AgentEarning> findById(UUID id);

	// ✅ findByAgent (with pagination)
	Page<AgentEarning> findByAgentId(UUID agentId, Pageable pageable);

	// Optional: if you need filtering (status/date etc.), you can use Specification
	// or custom queries

	// ✅ findByAssignment
	Optional<AgentEarning> findByAssignmentId(UUID assignmentId);

	// ✅ sumApprovedUnpaid
	@Query("""
			    SELECT COALESCE(SUM(e.netEarning), 0)
			    FROM AgentEarning e
			    WHERE e.agentId = :agentId
			    AND e.status = 'APPROVED'
			    AND e.paidAt IS NULL
			""")
	BigDecimal sumApprovedUnpaid(@Param("agentId") UUID agentId);

	// ✅ bulkUpdateStatus
	@Modifying
	@Transactional
	@Query("""
			    UPDATE AgentEarning e
			    SET e.status = :status
			    WHERE e.id IN :ids
			""")
	void bulkUpdateStatus(@Param("ids") List<UUID> ids, @Param("status") String status);

	// ✅ softDelete
	@Modifying
	@Transactional
	@Query("""
			    UPDATE AgentEarning e
			    SET e.isDeleted = true,
			        e.deletedBy = :actorId
			    WHERE e.id = :id
			""")
	void softDelete(@Param("id") UUID id, @Param("actorId") UUID actorId);
}
