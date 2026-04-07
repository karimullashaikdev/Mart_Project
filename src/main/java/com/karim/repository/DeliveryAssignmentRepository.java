package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.DeliveryAssignment;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID>{

	Optional<DeliveryAssignment> findByOrderId(UUID orderId);

	Optional<DeliveryAssignment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);
	
	List<DeliveryAssignment> findByAgentId(UUID agentId);
}
