package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.TrackingSession;

public interface TrackingSessionRepository extends JpaRepository<TrackingSession, UUID> {

    // ✅ create(data) → handled by save()

    // ✅ findByToken(wsToken)
    Optional<TrackingSession> findByWsToken(String wsToken);

    // ✅ findActiveByAssignment(assignmentId)
    @Query("""
        SELECT ts FROM TrackingSession ts
        WHERE ts.assignmentId = :assignmentId
        AND ts.status = 'ACTIVE'
    """)
    Optional<TrackingSession> findActiveByAssignment(@Param("assignmentId") UUID assignmentId);

    // ✅ update(id, data) → handled by save()

    // ✅ close(id)
    @Modifying
    @Query("""
        UPDATE TrackingSession ts
        SET ts.status = 'CLOSED',
            ts.closedAt = CURRENT_TIMESTAMP
        WHERE ts.id = :id
    """)
    void close(@Param("id") UUID id);
}

