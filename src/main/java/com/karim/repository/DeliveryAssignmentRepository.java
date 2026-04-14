package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.karim.entity.DeliveryAssignment;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID>{

	Optional<DeliveryAssignment> findByOrderId(UUID orderId);

	Optional<DeliveryAssignment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);
	
	List<DeliveryAssignment> findByAgentId(UUID agentId);
	

	    /**
	     * Returns all order IDs that are currently assigned (completedAt is null).
	     * Used to exclude them from the available orders list.
	     */
	    @Query("SELECT a.orderId FROM DeliveryAssignment a WHERE a.completedAt IS NULL")
	    List<UUID> findActiveAssignedOrderIds();

	    /**
	     * Returns all order IDs assigned to a specific agent (all time, including completed).
	     * Powers the "My Orders" panel.
	     */
	    @Query("SELECT a.orderId FROM DeliveryAssignment a WHERE a.agentId = :agentId")
	    List<UUID> findOrderIdsByAgentId(UUID agentId);

	    /**
	     * Checks if an order is already assigned and not yet completed.
	     * Used to prevent two agents from accepting the same order.
	     */
	    boolean existsByOrderIdAndCompletedAtIsNull(UUID orderId);

	    /**
	     * Checks if a specific agent owns an active (not yet completed) assignment for this order.
	     * Used for authorization before startDelivery / completeDelivery.
	     */
	    boolean existsByOrderIdAndAgentIdAndCompletedAtIsNull(UUID orderId, UUID agentId);

	    /**
	     * Finds the active (not completed) assignment for an order.
	     * Used when marking delivery complete.
	     */
	    Optional<DeliveryAssignment> findByOrderIdAndCompletedAtIsNull(UUID orderId);
	}

