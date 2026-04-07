package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.TrackingSession;

public interface TrackingSessionRepository extends JpaRepository<TrackingSession, UUID> {

	// ✅ create(data) → handled by save()

	// ✅ findByToken(wsToken)
	Optional<TrackingSession> findByWsToken(String wsToken);

	// ✅ findActiveByAssignment(assignmentId)
	@Query("""
			    SELECT ts FROM TrackingSession ts
			    WHERE ts.assignmentId = :assignmentId
			    AND ts.isActive = true
			""")
	Optional<TrackingSession> findActiveByAssignment(@Param("assignmentId") UUID assignmentId);

	// ✅ update(id, data) → handled by save()

	// ✅ close(id)
	@Modifying
	@Transactional
	@Query("""
			    UPDATE TrackingSession ts
			    SET ts.isActive = false,
			        ts.closedAt = CURRENT_TIMESTAMP
			    WHERE ts.id = :id
			""")
	void close(@Param("id") UUID id);

	Optional<TrackingSession> findByAssignmentIdAndUserIdAndIsActiveTrue(UUID assignmentId, UUID userId);

	Optional<TrackingSession> findByWsTokenAndIsActiveTrue(String wsToken);

	Optional<TrackingSession> findById(UUID id);

	Optional<TrackingSession> findByAssignmentIdAndIsActiveTrue(UUID assignmentId);
}
