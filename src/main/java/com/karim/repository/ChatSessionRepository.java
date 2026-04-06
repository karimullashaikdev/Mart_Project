package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    // ✅ findById
    Optional<ChatSession> findById(UUID id);

    // ✅ findByOrder
    Optional<ChatSession> findByOrderId(UUID orderId);

    // ✅ findActiveByOrder (status = 'ACTIVE')
    @Query("SELECT cs FROM ChatSession cs WHERE cs.orderId = :orderId AND cs.status = 'ACTIVE'")
    Optional<ChatSession> findActiveByOrder(@Param("orderId") UUID orderId);

    // create & update → handled by save()

    // softDelete → handled in service layer
}
