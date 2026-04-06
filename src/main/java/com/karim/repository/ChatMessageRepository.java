package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // ✅ findById
    Optional<ChatMessage> findById(UUID id);

    // ✅ findBySession with pagination
    Page<ChatMessage> findBySessionId(UUID sessionId, Pageable pageable);

    // create & update → save()

    // markRead / markAllRead / softDelete → handled in service layer
}
