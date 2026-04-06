package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationPingRepository extends JpaRepository<LocationPing, UUID> {

    // ✅ create(data) → handled by save()

    // ✅ findLatest(assignmentId)
    @Query("SELECT lp FROM LocationPing lp WHERE lp.assignmentId = :assignmentId ORDER BY lp.createdAt DESC")
    Optional<LocationPing> findLatest(@Param("assignmentId") UUID assignmentId);

    // ✅ findInRange(assignmentId, from, to)
    @Query("""
        SELECT lp FROM LocationPing lp
        WHERE lp.assignmentId = :assignmentId
        AND lp.createdAt BETWEEN :from AND :to
        ORDER BY lp.createdAt ASC
    """)
    List<LocationPing> findInRange(
            @Param("assignmentId") UUID assignmentId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // ✅ softDeleteByAssignment(assignmentId, actorId)
    @Modifying
    @Query("""
        UPDATE LocationPing lp
        SET lp.deleted = true,
            lp.deletedBy = :actorId,
            lp.deletedAt = CURRENT_TIMESTAMP
        WHERE lp.assignmentId = :assignmentId
    """)
    void softDeleteByAssignment(
            @Param("assignmentId") UUID assignmentId,
            @Param("actorId") UUID actorId
    );
}
