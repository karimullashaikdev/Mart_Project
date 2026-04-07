package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.AgentLocationPing;

public interface LocationPingRepository extends JpaRepository<AgentLocationPing, UUID> {

	// ✅ create(data) → handled by save()

	// ✅ findLatest(assignmentId)
	@Query("""
		    SELECT lp FROM AgentLocationPing lp
		    WHERE lp.assignmentId = :assignmentId
		    ORDER BY lp.recordedAt DESC
		""")
		Optional<AgentLocationPing> findLatest(@Param("assignmentId") UUID assignmentId);

	// ✅ findInRange(assignmentId, from, to)
	@Query("""
		    SELECT lp FROM AgentLocationPing lp
		    WHERE lp.assignmentId = :assignmentId
		    AND lp.recordedAt BETWEEN :from AND :to
		    ORDER BY lp.recordedAt ASC
		""")
		List<AgentLocationPing> findInRange(
		    @Param("assignmentId") UUID assignmentId,
		    @Param("from") LocalDateTime from,
		    @Param("to") LocalDateTime to
		);

	// ✅ softDeleteByAssignment(assignmentId, actorId)
	@Modifying
	@Transactional
	@Query("""
			    UPDATE AgentLocationPing lp
			    SET lp.isDeleted = true,
			        lp.deletedBy = :actorId,
			        lp.deletedAt = CURRENT_TIMESTAMP
			    WHERE lp.assignmentId = :assignmentId
			""")
	void softDeleteByAssignment(@Param("assignmentId") UUID assignmentId, @Param("actorId") UUID actorId);
}
