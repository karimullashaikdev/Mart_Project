package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.CreateAgentDto;
import com.karim.dto.CreatePricingConfigDto;
import com.karim.dto.DeliveryAgentResponse;
import com.karim.dto.DeliveryAssignmentFilter;
import com.karim.dto.DeliveryAssignmentResponse;
import com.karim.dto.DeliveryPricingConfigResponse;
import com.karim.entity.DeliveryAgent;
import com.karim.entity.DeliveryAssignment;
import com.karim.entity.DeliveryPricingConfig;
import com.karim.entity.DeliveryRide;
import com.karim.entity.Order;
import com.karim.entity.User;
import com.karim.enums.AvailabilityStatus;
import com.karim.enums.DeliveryStatus;
import com.karim.enums.OrderStatus;
import com.karim.enums.RideStatus;
import com.karim.enums.VehicleType;
import com.karim.repository.DeliveryAgentRepository;
import com.karim.repository.DeliveryAssignmentRepository;
import com.karim.repository.DeliveryPricingConfigRepository;
import com.karim.repository.DeliveryRideRepository;
import com.karim.repository.OrderRepository;
import com.karim.repository.UserRepository;
import com.karim.service.DeliveryService;
import com.karim.service.OrderService;

@Service
public class DeliveryServiceImpl implements DeliveryService {

	private final DeliveryAgentRepository deliveryAgentRepository;
	private final UserRepository userRepository;
	private final DeliveryAssignmentRepository deliveryAssignmentRepository;
	private final OrderRepository orderRepository;
	private final OrderService orderService;
	private final DeliveryRideRepository deliveryRideRepository;
	private final DeliveryPricingConfigRepository deliveryPricingConfigRepository;

	public DeliveryServiceImpl(DeliveryAgentRepository deliveryAgentRepository, UserRepository userRepository,
			DeliveryAssignmentRepository deliveryAssignmentRepository, OrderRepository orderRepository,
			OrderService orderService, DeliveryRideRepository deliveryRideRepository,DeliveryPricingConfigRepository deliveryPricingConfigRepository) {
		this.deliveryAgentRepository = deliveryAgentRepository;
		this.userRepository = userRepository;
		this.deliveryAssignmentRepository = deliveryAssignmentRepository;
		this.orderRepository = orderRepository;
		this.orderService = orderService;
		this.deliveryRideRepository = deliveryRideRepository;
		this.deliveryPricingConfigRepository=deliveryPricingConfigRepository;
	}

	@Transactional
	@Override
	public DeliveryAgentResponse createAgent(UUID userId, CreateAgentDto dto, UUID actorId) {

		// 🔍 1. Validate user exists
		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		// ⚠️ 2. Prevent duplicate agent for same user
		deliveryAgentRepository.findByUserId(userId).ifPresent(agent -> {
			throw new RuntimeException("Delivery agent already exists for this user");
		});

		// 🔄 3. Convert vehicle type
		VehicleType vehicleType;
		try {
			vehicleType = VehicleType.valueOf(dto.getVehicleType().toUpperCase());
		} catch (Exception e) {
			throw new RuntimeException("Invalid vehicle type");
		}

		// 🧾 4. Create entity
		DeliveryAgent agent = new DeliveryAgent();
		agent.setUserId(userId);
		agent.setVehicleType(vehicleType);
		agent.setVehicleNumber(dto.getVehicleNumber());
		agent.setLicenseNumber(dto.getLicenseNumber());
		agent.setAvailabilityStatus(AvailabilityStatus.OFFLINE);
		agent.setVerified(false);
		agent.setUpdatedBy(actorId);

		// 💾 5. Save
		DeliveryAgent savedAgent = deliveryAgentRepository.save(agent);

		// 📦 6. Map response
		return DeliveryAgentResponse.builder().id(savedAgent.getId()).userId(savedAgent.getUserId())
				.vehicleType(savedAgent.getVehicleType().name()).vehicleNumber(savedAgent.getVehicleNumber())
				.licenseNumber(savedAgent.getLicenseNumber())
				.availabilityStatus(savedAgent.getAvailabilityStatus().name()).isVerified(savedAgent.isVerified())
				.build();
	}

	@Override
	public DeliveryAgentResponse getAgent(UUID agentId) {

		// 🔍 1. Fetch agent
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// 📦 2. Map to response
		return DeliveryAgentResponse.builder().id(agent.getId()).userId(agent.getUserId())
				.vehicleType(agent.getVehicleType() != null ? agent.getVehicleType().name() : null)
				.vehicleNumber(agent.getVehicleNumber()).licenseNumber(agent.getLicenseNumber())
				.availabilityStatus(agent.getAvailabilityStatus() != null ? agent.getAvailabilityStatus().name() : null)
				.isVerified(agent.isVerified()).ratingAvg(agent.getRatingAvg())
				.totalDeliveries(agent.getTotalDeliveries()).totalEarningsAllTime(agent.getTotalEarningsAllTime())
				.walletBalance(agent.getWalletBalance()).build();
	}

	@Override
	public DeliveryAgentResponse getAgentByUserId(UUID userId) {

		// 🔍 1. Fetch agent by userId
		DeliveryAgent agent = deliveryAgentRepository.findByUserId(userId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found for this user"));

		// 📦 2. Map to response DTO
		return DeliveryAgentResponse.builder().id(agent.getId()).userId(agent.getUserId())
				.vehicleType(agent.getVehicleType() != null ? agent.getVehicleType().name() : null)
				.vehicleNumber(agent.getVehicleNumber()).licenseNumber(agent.getLicenseNumber())
				.availabilityStatus(agent.getAvailabilityStatus() != null ? agent.getAvailabilityStatus().name() : null)
				.isVerified(agent.isVerified()).ratingAvg(agent.getRatingAvg())
				.totalDeliveries(agent.getTotalDeliveries()).totalEarningsAllTime(agent.getTotalEarningsAllTime())
				.walletBalance(agent.getWalletBalance()).build();
	}

	@Transactional
	@Override
	public DeliveryAgentResponse updateAgent(UUID agentId, CreateAgentDto dto, UUID actorId) {

		// 🔍 1. Fetch agent
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// 🔄 2. Update vehicle type
		if (dto.getVehicleType() != null) {
			VehicleType vehicleType;
			try {
				vehicleType = VehicleType.valueOf(dto.getVehicleType().toUpperCase());
			} catch (Exception e) {
				throw new RuntimeException("Invalid vehicle type");
			}
			agent.setVehicleType(vehicleType);
		}

		// 🔄 3. Update vehicle number
		if (dto.getVehicleNumber() != null && !dto.getVehicleNumber().isBlank()) {
			agent.setVehicleNumber(dto.getVehicleNumber());
		}

		// 🔄 4. Update license number
		if (dto.getLicenseNumber() != null && !dto.getLicenseNumber().isBlank()) {
			agent.setLicenseNumber(dto.getLicenseNumber());
		}

		// 🔄 5. Update audit field
		agent.setUpdatedBy(actorId);

		// 💾 6. Save
		DeliveryAgent updatedAgent = deliveryAgentRepository.save(agent);

		// 📦 7. Map response
		return DeliveryAgentResponse.builder().id(updatedAgent.getId()).userId(updatedAgent.getUserId())
				.vehicleType(updatedAgent.getVehicleType() != null ? updatedAgent.getVehicleType().name() : null)
				.vehicleNumber(updatedAgent.getVehicleNumber()).licenseNumber(updatedAgent.getLicenseNumber())
				.availabilityStatus(
						updatedAgent.getAvailabilityStatus() != null ? updatedAgent.getAvailabilityStatus().name()
								: null)
				.isVerified(updatedAgent.isVerified()).ratingAvg(updatedAgent.getRatingAvg())
				.totalDeliveries(updatedAgent.getTotalDeliveries())
				.totalEarningsAllTime(updatedAgent.getTotalEarningsAllTime())
				.walletBalance(updatedAgent.getWalletBalance()).build();
	}

	@Transactional
	@Override
	public void setAvailability(UUID agentId, AvailabilityStatus status, UUID actorId) {

		// 🔍 1. Fetch agent
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// ⚠️ 2. Validate status (basic null check)
		if (status == null) {
			throw new RuntimeException("Invalid availability status");
		}

		// 🔄 3. Update availability status
		agent.setAvailabilityStatus(status);

		// 🔄 4. Audit field
		agent.setUpdatedBy(actorId);

		// 💾 5. Save
		deliveryAgentRepository.save(agent);
	}

	@Transactional
	@Override
	public void verifyAgent(UUID agentId, UUID actorId) {

		// 🔍 1. Fetch agent
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// ⚠️ 2. Prevent duplicate verification
		if (agent.isVerified()) {
			throw new RuntimeException("Agent is already verified");
		}

		// 🔄 3. Update verification status
		agent.setVerified(true);
		agent.setUpdatedBy(actorId);

		// 💾 4. Save
		deliveryAgentRepository.save(agent);
	}

	@Transactional
	@Override
	public void suspendAgent(UUID agentId, UUID actorId) {

		// 🔍 1. Fetch agent
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// ⚠️ 2. Prevent redundant update
		if (agent.getAvailabilityStatus() == AvailabilityStatus.SUSPENDED) {
			throw new RuntimeException("Agent already suspended");
		}

		// 🔄 3. Update availability status
		agent.setAvailabilityStatus(AvailabilityStatus.SUSPENDED);

		// 🔄 4. Audit fields
		agent.setUpdatedBy(actorId);
		agent.setUpdatedAt(LocalDateTime.now());

		// 💾 5. Save
		deliveryAgentRepository.save(agent);
	}

	@Transactional
	@Override
	public void softDeleteAgent(UUID agentId, UUID actorId) {

		// 🔍 1. Fetch agent
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// ⚠️ 2. Prevent duplicate deletion
		if (agent.isDeleted()) {
			throw new RuntimeException("Delivery agent already deleted");
		}

		// 🔄 3. Soft delete update
		agent.setDeleted(true);
		agent.setDeletedAt(LocalDateTime.now());
		agent.setDeletedBy(actorId);
		agent.setUpdatedBy(actorId);

		// 💾 4. Save
		deliveryAgentRepository.save(agent);
	}

	@Override
	public List<DeliveryAgentResponse> listAvailableAgents() {

		// 🔍 1. Fetch available & verified agents
		List<DeliveryAgent> agents = deliveryAgentRepository.findAvailable();

		// 📦 2. Map to response DTO
		return agents.stream().map(agent -> DeliveryAgentResponse.builder().id(agent.getId()).userId(agent.getUserId())
				.vehicleType(agent.getVehicleType() != null ? agent.getVehicleType().name() : null)
				.vehicleNumber(agent.getVehicleNumber()).licenseNumber(agent.getLicenseNumber())
				.availabilityStatus(agent.getAvailabilityStatus() != null ? agent.getAvailabilityStatus().name() : null)
				.isVerified(agent.isVerified()).ratingAvg(agent.getRatingAvg())
				.totalDeliveries(agent.getTotalDeliveries()).totalEarningsAllTime(agent.getTotalEarningsAllTime())
				.walletBalance(agent.getWalletBalance()).build()).toList();
	}

	@Transactional
	@Override
	public void assignDelivery(UUID orderId, UUID agentId, UUID actorId) {

		// 🔍 1. Validate order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate order status (only allow dispatch-related orders)
		if (order.getStatus() != OrderStatus.DISPATCHED) {
			throw new RuntimeException("Order is not ready for delivery assignment");
		}

		// 🔍 3. Validate delivery agent
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// ⚠️ 4. Validate agent availability
		if (agent.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
			throw new RuntimeException("Delivery agent is not available");
		}

		if (!agent.isVerified()) {
			throw new RuntimeException("Delivery agent is not verified");
		}

		// 🔍 5. Prevent duplicate assignment for same order
		Optional<DeliveryAssignment> existingAssignment = deliveryAssignmentRepository.findByOrderId(orderId);

		if (existingAssignment.isPresent()) {
			throw new RuntimeException("Order already assigned to a delivery agent");
		}

		// 🧾 6. Create delivery assignment
		DeliveryAssignment assignment = new DeliveryAssignment();
		assignment.setOrderId(orderId);
		assignment.setAgentId(agentId);
		assignment.setStatus(DeliveryStatus.ASSIGNED);
		assignment.setAttemptNumber(1);
		assignment.setAssignedBy(actorId);
		assignment.setAssignedAt(LocalDateTime.now());

		deliveryAssignmentRepository.save(assignment);

		// 🔄 7. Update agent status
		agent.setAvailabilityStatus(AvailabilityStatus.ON_DELIVERY);
		agent.setUpdatedBy(actorId);

		deliveryAgentRepository.save(agent);

		// 🔄 8. Update order status
		order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
		orderRepository.save(order);
	}

	@Transactional
	@Override
	public void acceptAssignment(UUID assignmentId, UUID agentId) {

		// 🔍 1. Fetch assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// ⚠️ 2. Validate agent ownership
		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		// ⚠️ 3. Validate current status
		if (assignment.getStatus() != DeliveryStatus.ASSIGNED) {
			throw new RuntimeException("Only assigned deliveries can be accepted");
		}

		// 🔄 4. Update assignment
		assignment.setStatus(DeliveryStatus.ACCEPTED);
		assignment.setAcceptedAt(LocalDateTime.now());
		assignment.setUpdatedAt(LocalDateTime.now());

		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void rejectAssignment(UUID assignmentId, UUID agentId, String reason) {

		// 🔍 1. Fetch assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// ⚠️ 2. Validate agent ownership
		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		// ⚠️ 3. Prevent duplicate rejection
		if (assignment.getStatus() == DeliveryStatus.REJECTED) {
			throw new RuntimeException("Assignment already rejected");
		}

		// 🔄 4. Update assignment
		assignment.setStatus(DeliveryStatus.REJECTED);
		assignment.setRejectedAt(LocalDateTime.now());
		assignment.setFailureReason(reason);
		assignment.setUpdatedAt(LocalDateTime.now());

		deliveryAssignmentRepository.save(assignment);

		// ⚠️ 5. Reassignment placeholder (since logic is not defined yet)
		// You can implement agent selection logic later
		// Example: find another available verified agent and assign

		// TODO: implement reassignment strategy
	}

	@Transactional
	@Override
	public void markPickedUp(UUID assignmentId, UUID agentId) {

		// 🔍 1. Fetch assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// ⚠️ 2. Validate agent ownership
		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		// ⚠️ 3. Validate current status
		if (assignment.getStatus() != DeliveryStatus.ASSIGNED && assignment.getStatus() != DeliveryStatus.ACCEPTED) {
			throw new RuntimeException("Pickup not allowed at current stage");
		}

		// ⚠️ 4. Prevent duplicate action
		if (assignment.getPickedUpAt() != null) {
			throw new RuntimeException("Order already marked as picked up");
		}

		// 🔄 5. Update assignment
		assignment.setStatus(DeliveryStatus.PICKED_UP);
		assignment.setPickedUpAt(LocalDateTime.now());

		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void markInTransit(UUID assignmentId, UUID agentId) {

		// 🔍 1. Fetch assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// ⚠️ 2. Validate agent ownership
		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		// ⚠️ 3. Validate current status
		if (assignment.getStatus() != DeliveryStatus.PICKED_UP) {
			throw new RuntimeException("Delivery can be marked in transit only after pickup");
		}

		// 🔄 4. Update status
		assignment.setStatus(DeliveryStatus.IN_TRANSIT);

		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void markDelivered(UUID assignmentId, UUID agentId, String proofUrl) {

		// 🔍 1. Fetch assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// ⚠️ 2. Validate agent ownership
		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this delivery");
		}

		// ⚠️ 3. Validate current status
		if (assignment.getStatus() != DeliveryStatus.PICKED_UP && assignment.getStatus() != DeliveryStatus.IN_TRANSIT) {
			throw new RuntimeException("Delivery cannot be marked as delivered at this stage");
		}

		// 🔄 4. Update assignment
		assignment.setStatus(DeliveryStatus.DELIVERED);
		assignment.setDeliveryProofUrl(proofUrl);
		assignment.setDeliveredAt(LocalDateTime.now());
		assignment.setUpdatedAt(LocalDateTime.now());

		deliveryAssignmentRepository.save(assignment);

		// 🔍 5. Update agent status back to AVAILABLE
		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		agent.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
		deliveryAgentRepository.save(agent);

		// 🔄 6. Call OrderService to update order status
		orderService.markDelivered(assignment.getOrderId(), agentId);
	}

	@Transactional
	@Override
	public void markFailed(UUID assignmentId, UUID agentId, String reason) {

		// 🔍 1. Fetch assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// ⚠️ 2. Validate agent ownership
		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		// ⚠️ 3. Prevent invalid transitions
		if (assignment.getStatus() == DeliveryStatus.DELIVERED) {
			throw new RuntimeException("Cannot mark a delivered assignment as failed");
		}

		if (assignment.getStatus() == DeliveryStatus.REJECTED) {
			throw new RuntimeException("Assignment already rejected");
		}

		// 🔄 4. Update assignment
		assignment.setStatus(DeliveryStatus.FAILED);
		assignment.setFailureReason(reason);
		assignment.setUpdatedAt(LocalDateTime.now());

		deliveryAssignmentRepository.save(assignment);
	}

	@Override
	public DeliveryAssignmentResponse getAssignment(UUID assignmentId) {

		// 🔍 1. Fetch assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// 📦 2. Map to response DTO
		return DeliveryAssignmentResponse.builder().id(assignment.getId()).orderId(assignment.getOrderId())
				.agentId(assignment.getAgentId()).status(assignment.getStatus().name())
				.attemptNumber(assignment.getAttemptNumber()).deliveryProofUrl(assignment.getDeliveryProofUrl())
				.failureReason(assignment.getFailureReason()).assignedBy(assignment.getAssignedBy())
				.assignedAt(assignment.getAssignedAt()).acceptedAt(assignment.getAcceptedAt())
				.rejectedAt(assignment.getRejectedAt()).pickedUpAt(assignment.getPickedUpAt())
				.deliveredAt(assignment.getDeliveredAt()).build();
	}

	@Override
	public DeliveryAssignmentResponse getAssignmentByOrder(UUID orderId) {

		// 🔍 1. Fetch latest assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found for this order"));

		// 📦 2. Map to response DTO
		return DeliveryAssignmentResponse.builder().id(assignment.getId()).orderId(assignment.getOrderId())
				.agentId(assignment.getAgentId()).status(assignment.getStatus().name())
				.attemptNumber(assignment.getAttemptNumber()).deliveryProofUrl(assignment.getDeliveryProofUrl())
				.failureReason(assignment.getFailureReason()).assignedAt(assignment.getAssignedAt())
				.acceptedAt(assignment.getAcceptedAt()).rejectedAt(assignment.getRejectedAt())
				.pickedUpAt(assignment.getPickedUpAt()).deliveredAt(assignment.getDeliveredAt()).build();
	}

	@Override
	public List<DeliveryAssignmentResponse> listAssignmentsByAgent(UUID agentId, DeliveryAssignmentFilter filter) {

		// 🔍 1. Validate agent exists
		deliveryAgentRepository.findById(agentId).orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		// 📦 2. Fetch assignments
		List<DeliveryAssignment> assignments = deliveryAssignmentRepository.findByAgentId(agentId);

		// 🔎 3. Apply filters in memory (aligned with simple filter approach)
		return assignments.stream().filter(a -> {

			// status filter
			if (filter.getStatus() != null && !filter.getStatus().isEmpty()) {
				if (!a.getStatus().name().equalsIgnoreCase(filter.getStatus())) {
					return false;
				}
			}

			// date range filter (assignedAt)
			if (filter.getFromDate() != null) {
				if (a.getAssignedAt() == null || a.getAssignedAt().toLocalDate().isBefore(filter.getFromDate())) {
					return false;
				}
			}

			if (filter.getToDate() != null) {
				if (a.getAssignedAt() == null || a.getAssignedAt().toLocalDate().isAfter(filter.getToDate())) {
					return false;
				}
			}

			return true;
		}).map(a -> DeliveryAssignmentResponse.builder().id(a.getId()).orderId(a.getOrderId()).agentId(a.getAgentId())
				.status(a.getStatus().name()).attemptNumber(a.getAttemptNumber())
				.deliveryProofUrl(a.getDeliveryProofUrl()).failureReason(a.getFailureReason())
				.assignedAt(a.getAssignedAt()).acceptedAt(a.getAcceptedAt()).pickedUpAt(a.getPickedUpAt())
				.deliveredAt(a.getDeliveredAt()).build()).toList();
	}

	@Transactional
	@Override
	public void startRide(UUID assignmentId, UUID agentId, Double startLat, Double startLng) {

		// 🔍 1. Validate assignment
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		// ⚠️ 2. Validate agent ownership
		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		// ⚠️ 3. Validate assignment status
		if (assignment.getStatus() != DeliveryStatus.ASSIGNED && assignment.getStatus() != DeliveryStatus.ACCEPTED) {
			throw new RuntimeException("Ride can only be started for assigned/accepted deliveries");
		}

		// 🔍 4. Prevent duplicate ride
		deliveryRideRepository.findByAssignmentId(assignmentId).ifPresent(r -> {
			throw new RuntimeException("Ride already started for this assignment");
		});

		// 🧾 5. Create ride
		DeliveryRide ride = new DeliveryRide();
		ride.setAssignmentId(assignmentId);
		ride.setAgentId(agentId);
		ride.setOrderId(assignment.getOrderId());
		ride.setStartLatitude(startLat);
		ride.setStartLongitude(startLng);

		// status, rideStartedAt handled by @PrePersist
		ride.setStatus(RideStatus.STARTED);
		ride.setRideStartedAt(LocalDateTime.now());

		deliveryRideRepository.save(ride);

		// 🔄 6. Update assignment status
		assignment.setStatus(DeliveryStatus.IN_TRANSIT);
		assignment.setPickedUpAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void endRide(UUID rideId, UUID agentId, Double endLat, Double endLng, Double distanceKm) {

		// 🔍 1. Fetch ride
		DeliveryRide ride = deliveryRideRepository.findById(rideId)
				.orElseThrow(() -> new RuntimeException("Ride not found"));

		// ⚠️ 2. Validate agent
		if (!ride.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this ride");
		}

		// ⚠️ 3. Validate ride status
		if (ride.getStatus() != RideStatus.STARTED) {
			throw new RuntimeException("Ride cannot be ended at this stage");
		}

		// 🔄 4. Update ride end details
		ride.setEndLatitude(endLat);
		ride.setEndLongitude(endLng);
		ride.setDistanceKm(distanceKm);

		ride.setRideEndedAt(LocalDateTime.now());

		// 💰 5. Calculate total fare using existing fields
		float baseAmount = ride.getBaseAmount();
		float kmAmount = ride.getKmAmount();
		float surgeAmount = ride.getSurgeAmount();

		float totalFare = baseAmount + kmAmount + surgeAmount;

		ride.setTotalFare(totalFare);

		// 🔄 6. Update status
		ride.setStatus(RideStatus.COMPLETED);

		// 💾 7. Save ride
		deliveryRideRepository.save(ride);

		// 🔄 8. Optional: Update assignment status (if needed in your flow)
		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(ride.getAssignmentId())
				.orElseThrow(() -> new RuntimeException("Assignment not found"));

		assignment.setStatus(DeliveryStatus.DELIVERED);
		assignment.setDeliveredAt(LocalDateTime.now());

		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void cancelRide(UUID rideId, UUID agentId) {

		// 🔍 1. Fetch ride
		DeliveryRide ride = deliveryRideRepository.findById(rideId)
				.orElseThrow(() -> new RuntimeException("Ride not found"));

		// ⚠️ 2. Validate agent ownership
		if (!ride.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized to cancel this ride");
		}

		// ⚠️ 3. Validate current status
		if (ride.getStatus() == RideStatus.CANCELLED) {
			throw new RuntimeException("Ride already cancelled");
		}

		if (ride.getStatus() == RideStatus.COMPLETED) {
			throw new RuntimeException("Cannot cancel a completed ride");
		}

		// 🔄 4. Update ride status
		ride.setStatus(RideStatus.CANCELLED);
		ride.setUpdatedAt(LocalDateTime.now());

		deliveryRideRepository.save(ride);
	}

	@Override
	public DeliveryRide getRide(UUID rideId) {

		// 🔍 1. Fetch ride
		DeliveryRide ride = deliveryRideRepository.findById(rideId)
				.orElseThrow(() -> new RuntimeException("Ride not found"));

		// 📦 2. Return entity (as per your current pattern)
		return ride;
	}

	@Transactional
	@Override
	public DeliveryPricingConfigResponse createPricingConfig(CreatePricingConfigDto dto, UUID actorId) {

		// 🧾 1. Create entity
		DeliveryPricingConfig config = new DeliveryPricingConfig();

		config.setConfigName(dto.getConfigName());
		config.setBasePrice(dto.getBasePrice());
		config.setPricePerKm(dto.getPricePerKm());
		config.setMinDistanceKm(dto.getMinDistanceKm());
		config.setSurgeMultiplier(dto.getSurgeMultiplier() != null ? dto.getSurgeMultiplier() : 1.0f);
		config.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
		config.setDescription(dto.getDescription());
		config.setEffectiveFrom(dto.getEffectiveFrom());
		config.setEffectiveTo(dto.getEffectiveTo());
		config.setCreatedBy(actorId);

		// 💾 2. Save
		DeliveryPricingConfig saved = deliveryPricingConfigRepository.save(config);

		// 📦 3. Map response
		return DeliveryPricingConfigResponse.builder().id(saved.getId()).configName(saved.getConfigName())
				.basePrice(saved.getBasePrice()).pricePerKm(saved.getPricePerKm())
				.minDistanceKm(saved.getMinDistanceKm()).surgeMultiplier(saved.getSurgeMultiplier())
				.isActive(saved.getIsActive()).description(saved.getDescription())
				.effectiveFrom(saved.getEffectiveFrom()).effectiveTo(saved.getEffectiveTo()).build();
	}
	
	@Override
	public DeliveryPricingConfigResponse getActivePricingConfig() {

	    LocalDateTime now = LocalDateTime.now();

	    List<DeliveryPricingConfig> configs = deliveryPricingConfigRepository.findActiveConfigs(now);

	    if (configs.isEmpty()) {
	        throw new RuntimeException("No active pricing configuration found");
	    }

	    // Pick the most recent effective config (first after ordering)
	    DeliveryPricingConfig config = configs.get(0);

	    return DeliveryPricingConfigResponse.builder()
	            .id(config.getId())
	            .configName(config.getConfigName())
	            .basePrice(config.getBasePrice())
	            .pricePerKm(config.getPricePerKm())
	            .minDistanceKm(config.getMinDistanceKm())
	            .surgeMultiplier(config.getSurgeMultiplier())
	            .isActive(config.getIsActive())
	            .description(config.getDescription())
	            .effectiveFrom(config.getEffectiveFrom())
	            .effectiveTo(config.getEffectiveTo())
	            .build();
	}
	
	@Transactional
	@Override
	public DeliveryPricingConfigResponse updatePricingConfig(UUID configId, CreatePricingConfigDto dto, UUID actorId) {

	    // 1. Fetch existing config
	    DeliveryPricingConfig config = deliveryPricingConfigRepository.findById(configId)
	            .orElseThrow(() -> new RuntimeException("Pricing config not found with id: " + configId));

	    // 2. Update fields (only if provided)
	    if (dto.getConfigName() != null) {
	        config.setConfigName(dto.getConfigName());
	    }

	    if (dto.getBasePrice() != null) {
	        config.setBasePrice(dto.getBasePrice());
	    }

	    if (dto.getPricePerKm() != null) {
	        config.setPricePerKm(dto.getPricePerKm());
	    }

	    if (dto.getMinDistanceKm() != null) {
	        config.setMinDistanceKm(dto.getMinDistanceKm());
	    }

	    if (dto.getSurgeMultiplier() != null) {
	        config.setSurgeMultiplier(dto.getSurgeMultiplier());
	    }

	    if (dto.getIsActive() != null) {
	        config.setIsActive(dto.getIsActive());
	    }

	    if (dto.getDescription() != null) {
	        config.setDescription(dto.getDescription());
	    }

	    if (dto.getEffectiveFrom() != null) {
	        config.setEffectiveFrom(dto.getEffectiveFrom());
	    }

	    if (dto.getEffectiveTo() != null) {
	        config.setEffectiveTo(dto.getEffectiveTo());
	    }

	    // audit field
	    config.setUpdatedBy(actorId);

	    // 3. Save updated entity
	    DeliveryPricingConfig updated = deliveryPricingConfigRepository.save(config);

	    // 4. Map to response
	    return DeliveryPricingConfigResponse.builder()
	            .id(updated.getId())
	            .configName(updated.getConfigName())
	            .basePrice(updated.getBasePrice())
	            .pricePerKm(updated.getPricePerKm())
	            .minDistanceKm(updated.getMinDistanceKm())
	            .surgeMultiplier(updated.getSurgeMultiplier())
	            .isActive(updated.getIsActive())
	            .description(updated.getDescription())
	            .effectiveFrom(updated.getEffectiveFrom())
	            .effectiveTo(updated.getEffectiveTo())
	            .build();
	}
	
	@Transactional
	@Override
	public void deactivatePricingConfig(UUID configId, UUID actorId) {

	    // Optional: validate existence
	    DeliveryPricingConfig config = deliveryPricingConfigRepository.findById(configId)
	            .orElseThrow(() -> new RuntimeException("Pricing config not found"));

	    // Deactivate
	    deliveryPricingConfigRepository.deactivateById(configId, actorId);
	}
	
	@Override
	public List<DeliveryPricingConfigResponse> listPricingConfigs() {

	    List<DeliveryPricingConfig> configs = deliveryPricingConfigRepository.findAll();

	    return configs.stream()
	            .map(config -> DeliveryPricingConfigResponse.builder()
	                    .id(config.getId())
	                    .configName(config.getConfigName())
	                    .basePrice(config.getBasePrice())
	                    .pricePerKm(config.getPricePerKm())
	                    .minDistanceKm(config.getMinDistanceKm())
	                    .surgeMultiplier(config.getSurgeMultiplier())
	                    .isActive(config.getIsActive())
	                    .description(config.getDescription())
	                    .effectiveFrom(config.getEffectiveFrom())
	                    .effectiveTo(config.getEffectiveTo())
	                    .build()
	            )
	            .toList();
	}

}