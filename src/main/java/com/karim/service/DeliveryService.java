package com.karim.service;

import java.util.List;
import java.util.UUID;

import com.karim.dto.CreateAgentDto;
import com.karim.dto.CreatePricingConfigDto;
import com.karim.dto.DeliveryAgentResponse;
import com.karim.dto.DeliveryAssignmentFilter;
import com.karim.dto.DeliveryAssignmentResponse;
import com.karim.dto.DeliveryPricingConfigResponse;
import com.karim.entity.DeliveryRide;
import com.karim.enums.AvailabilityStatus;

public interface DeliveryService {

	DeliveryAgentResponse createAgent(UUID userId, CreateAgentDto dto, UUID actorId);

	DeliveryAgentResponse getAgent(UUID agentId);

	DeliveryAgentResponse getAgentByUserId(UUID userId);

	DeliveryAgentResponse updateAgent(UUID agentId, CreateAgentDto dto, UUID actorId);

	void setAvailability(UUID agentId, AvailabilityStatus status, UUID actorId);

	void verifyAgent(UUID agentId, UUID actorId);

	void suspendAgent(UUID agentId, UUID actorId);

	void softDeleteAgent(UUID agentId, UUID actorId);

	List<DeliveryAgentResponse> listAvailableAgents();

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

	void startRide(UUID assignmentId, UUID agentId, Double startLat, Double startLng);

	void endRide(UUID rideId, UUID agentId, Double endLat, Double endLng, Double distanceKm);

	void cancelRide(UUID rideId, UUID agentId);

	DeliveryRide getRide(UUID rideId);

	DeliveryPricingConfigResponse createPricingConfig(CreatePricingConfigDto dto, UUID actorId);

	DeliveryPricingConfigResponse getActivePricingConfig();
	
	DeliveryPricingConfigResponse updatePricingConfig(UUID configId, CreatePricingConfigDto dto, UUID actorId);
	
	void deactivatePricingConfig(UUID configId, UUID actorId);
	
	List<DeliveryPricingConfigResponse> listPricingConfigs();
	
	
}
