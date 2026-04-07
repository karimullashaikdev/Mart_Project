package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.AgentPayout;

public interface AgentPayoutRepository extends JpaRepository<AgentPayout, UUID> {
	Optional<AgentPayout> findById(UUID id);
}
