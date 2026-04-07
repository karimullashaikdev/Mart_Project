package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.AgentLocationPing;

public interface AgentLocationPingRepository extends JpaRepository<AgentLocationPing, UUID> {

	Optional<AgentLocationPing> findTopByAssignmentIdOrderByPingSequenceDesc(UUID assignmentId);

	@Query("""
		    SELECT p FROM AgentLocationPing p
		    WHERE p.assignmentId = :assignmentId
		    AND (:from IS NULL OR p.recordedAt >= :from)
		    AND (:to IS NULL OR p.recordedAt <= :to)
		    ORDER BY p.pingSequence ASC
		""")
		List<AgentLocationPing> findPingHistory(
		        @Param("assignmentId") UUID assignmentId,
		        @Param("from") LocalDateTime from,
		        @Param("to") LocalDateTime to
		);

	@Modifying
	@Query("UPDATE AgentLocationPing p SET p.isDeleted = true, p.deletedBy = :actorId WHERE p.assignmentId = :assignmentId")
	void softDeleteByAssignmentId(@Param("assignmentId") UUID assignmentId, @Param("actorId") UUID actorId);
}
