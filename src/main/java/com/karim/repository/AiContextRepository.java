package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiContextRepository extends JpaRepository<AiContext, UUID> {

    // ✅ findBySession
    Optional<AiContext> findBySessionId(UUID sessionId);

    // create & update → save()

    // softDelete → handled in service layer
}
