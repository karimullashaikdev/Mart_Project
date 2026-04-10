package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.RazorpayWebhookLog;

public interface RazorpayWebhookLogRepository extends JpaRepository<RazorpayWebhookLog, UUID> {

	Optional<RazorpayWebhookLog> findByEventId(String eventId);
}
