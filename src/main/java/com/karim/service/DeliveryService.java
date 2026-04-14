package com.karim.service;

import java.util.List;
import java.util.UUID;

import com.karim.dto.CreateAgentDto;
import com.karim.dto.CreatePricingConfigDto;
import com.karim.dto.DeliveryAgentResponse;
import com.karim.dto.DeliveryAssignmentFilter;
import com.karim.dto.DeliveryAssignmentResponse;
import com.karim.dto.DeliveryPricingConfigResponse;
import com.karim.dto.OrderNotificationDto;
import com.karim.entity.DeliveryRide;
import com.karim.enums.AvailabilityStatus;

public interface DeliveryService {

	// ── Agent ─────────────────────────────────────────────────────────────────
	DeliveryAgentResponse createAgent(UUID userId, CreateAgentDto dto, UUID actorId);

	DeliveryAgentResponse getAgent(UUID agentId);

	DeliveryAgentResponse getAgentByUserId(UUID userId);

	DeliveryAgentResponse updateAgent(UUID agentId, CreateAgentDto dto, UUID actorId);

	void setAvailability(UUID agentId, AvailabilityStatus status, UUID actorId);

	void verifyAgent(UUID agentId, UUID actorId);

	void suspendAgent(UUID agentId, UUID actorId);

	void softDeleteAgent(UUID agentId, UUID actorId);

	List<DeliveryAgentResponse> listAvailableAgents();

	// ── Orders for Dashboard ──────────────────────────────────────────────────
	/**
	 * Fetch CONFIRMED + PROCESSING + DISPATCHED + OUT_FOR_DELIVERY orders for
	 * delivery dashboard page load
	 */
	List<OrderNotificationDto> getActiveOrdersForDashboard();

	/** Delivery agent accepts a CONFIRMED order — moves it to PROCESSING */
	void acceptOrderByAgent(UUID orderId, UUID userId);

	// ── Assignment ────────────────────────────────────────────────────────────
	void assignDelivery(UUID orderId, UUID agentId, UUID actorId);

	void acceptAssignment(UUID assignmentId, UUID agentId);

	void rejectAssignment(UUID assignmentId, UUID agentId, String reason);

	void markPickedUp(UUID assignmentId, UUID agentId);

	void markInTransit(UUID assignmentId, UUID agentId);

	void markDelivered(UUID assignmentId, UUID agentId, String proofUrl);

	void markFailed(UUID assignmentId, UUID agentId, String reason);

	DeliveryAssignmentResponse getAssignment(UUID assignmentId);

	DeliveryAssignmentResponse getAssignmentByOrder(UUID orderId);

	List<DeliveryAssignmentResponse> listAssignmentsByAgent(UUID agentId, DeliveryAssignmentFilter filter);

	// ── Ride ──────────────────────────────────────────────────────────────────
	void startRide(UUID assignmentId, UUID agentId, Double startLat, Double startLng);

	void endRide(UUID rideId, UUID agentId, Double endLat, Double endLng, Double distanceKm);

	void cancelRide(UUID rideId, UUID agentId);

	DeliveryRide getRide(UUID rideId);

	// ── Pricing ───────────────────────────────────────────────────────────────
	DeliveryPricingConfigResponse createPricingConfig(CreatePricingConfigDto dto, UUID actorId);

	DeliveryPricingConfigResponse getActivePricingConfig();

	DeliveryPricingConfigResponse updatePricingConfig(UUID configId, CreatePricingConfigDto dto, UUID actorId);

	void deactivatePricingConfig(UUID configId, UUID actorId);

	List<DeliveryPricingConfigResponse> listPricingConfigs();

	/**
	 * Returns orders in PROCESSING status that are not yet assigned to any agent.
	 * Shown in the "Available Orders" panel.
	 */
	List<OrderNotificationDto> getAvailableOrders(UUID agentId);

	/**
	 * Agent accepts an order. - Validates order is in PROCESSING status. - Creates
	 * a DeliveryAssignment record linking this agent to the order. - Transitions
	 * order: PROCESSING → DISPATCHED.
	 */
	void acceptOrder(UUID orderId, UUID agentId);

	/**
	 * Agent starts riding to the customer. - Validates this agent owns the
	 * assignment. - Transitions order: DISPATCHED → OUT_FOR_DELIVERY.
	 */
	void startDelivery(UUID orderId, UUID agentId);

	/**
	 * Agent submits delivery OTP after reaching customer. - Validates OTP matches
	 * the one sent to customer. - Transitions order: OUT_FOR_DELIVERY → DELIVERED.
	 * - Marks DeliveryAssignment as completed with timestamp. Throws
	 * RuntimeException if OTP is wrong.
	 */
	void completeDelivery(UUID orderId, UUID agentId, String otp);

	/**
	 * Returns all orders assigned to this agent (DISPATCHED, OUT_FOR_DELIVERY,
	 * DELIVERED). Powers the "My Orders" panel.
	 */
	List<OrderNotificationDto> getMyOrders(UUID agentId);

	/**
	 * Stores or broadcasts the agent's current GPS position.
	 */
	void updateAgentLocation(UUID orderId, UUID agentId, double latitude, double longitude);

	/**
	 * Cleans up location session when delivery completes or agent logs out.
	 */
	void stopAgentLocation(UUID orderId, UUID agentId);
}