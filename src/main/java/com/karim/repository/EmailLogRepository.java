package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.EmailLog;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    // ✅ findById
    Optional<EmailLog> findById(UUID id);

    // ✅ create(data) → handled by save()

    // ✅ update(id, data) → handled by save()

    // ✅ findFailedForRetry(maxRetries)
    @Query("SELECT e FROM EmailLog e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetries")
    List<EmailLog> findFailedForRetry(@Param("maxRetries") int maxRetries);
}
