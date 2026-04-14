package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.CreateAgentDto;
import com.karim.dto.CreatePricingConfigDto;
import com.karim.dto.DeliveryAgentResponse;
import com.karim.dto.DeliveryAssignmentFilter;
import com.karim.dto.DeliveryAssignmentResponse;
import com.karim.dto.DeliveryPricingConfigResponse;
import com.karim.dto.OrderItemResponseDto;
import com.karim.dto.OrderNotificationDto;
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
import com.karim.repository.OrderItemRepository;
import com.karim.repository.OrderRepository;
import com.karim.repository.UserRepository;
import com.karim.service.DeliveryService;
import com.karim.service.OtpService;
import com.karim.service.OrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DeliveryServiceImpl implements DeliveryService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeliveryServiceImpl.class);

	private final DeliveryAgentRepository deliveryAgentRepository;
	private final UserRepository userRepository;
	private final DeliveryAssignmentRepository deliveryAssignmentRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderService orderService;
	private final DeliveryRideRepository deliveryRideRepository;
	private final DeliveryPricingConfigRepository deliveryPricingConfigRepository;
	private final com.karim.service.OtpService otpService;

	public DeliveryServiceImpl(DeliveryAgentRepository deliveryAgentRepository, UserRepository userRepository,
			DeliveryAssignmentRepository deliveryAssignmentRepository, OrderRepository orderRepository,
			OrderItemRepository orderItemRepository,
			OrderService orderService, DeliveryRideRepository deliveryRideRepository,
			DeliveryPricingConfigRepository deliveryPricingConfigRepository,
			com.karim.service.OtpService otpService) {
		this.deliveryAgentRepository = deliveryAgentRepository;
		this.userRepository = userRepository;
		this.deliveryAssignmentRepository = deliveryAssignmentRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.orderService = orderService;
		this.deliveryRideRepository = deliveryRideRepository;
		this.deliveryPricingConfigRepository = deliveryPricingConfigRepository;
		this.otpService = otpService;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// ✅ NEW — Active orders for delivery dashboard (page load)
	// ─────────────────────────────────────────────────────────────────────────

	@Override
	@Transactional(readOnly = true)
	public List<OrderNotificationDto> getActiveOrdersForDashboard() {
	    List<Order> activeOrders = orderRepository.findByStatusIn(
	            List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
	                    OrderStatus.DISPATCHED, OrderStatus.OUT_FOR_DELIVERY));

	    return activeOrders.stream()
	            .map(this::mapOrderToNotificationDto)
	            .toList();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// ✅ NEW — Delivery agent accepts a CONFIRMED order → moves to PROCESSING
	// ─────────────────────────────────────────────────────────────────────────

	@Override
	@Transactional
	public void acceptOrderByAgent(UUID orderId, UUID userId) {

		// 1. Validate agent exists and is available
		DeliveryAgent agent = deliveryAgentRepository.findByUserId(userId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found for this user"));

		if (!agent.isVerified()) {
			throw new RuntimeException("Agent is not verified yet");
		}

		if (agent.getAvailabilityStatus() == AvailabilityStatus.SUSPENDED) {
			throw new RuntimeException("Suspended agents cannot accept orders");
		}

		// 2. Validate order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// Only CONFIRMED orders can be accepted by a delivery agent
		if (order.getStatus() != OrderStatus.CONFIRMED) {
			throw new RuntimeException("Only CONFIRMED orders can be accepted");
		}

		// 3. Move order to PROCESSING
		order.setStatus(OrderStatus.PROCESSING);
		order.setUpdatedBy(userId);
		orderRepository.save(order);

		// 4. Set agent to ON_DELIVERY
		agent.setAvailabilityStatus(AvailabilityStatus.ON_DELIVERY);
		agent.setUpdatedBy(userId);
		deliveryAgentRepository.save(agent);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Private mapper — Order entity → OrderNotificationDto (no lazy proxies)
	// ─────────────────────────────────────────────────────────────────────────

	private OrderNotificationDto mapOrderToNotificationDto(Order order) {

		// ✅ Safely read User primitive fields (in same transaction)
		String customerName = null;
		String customerPhone = null;
		if (order.getUser() != null) {
			customerName = order.getUser().getFullName(); // adjust to your User field
			customerPhone = order.getUser().getPhone(); // adjust to your User field
		}

		// ✅ Safely flatten Address fields
		String addressLine = null;
		String city = null;
		String pincode = null;
		Double latitude = null;
		Double longitude = null;
		if (order.getAddress() != null) {
			addressLine = order.getAddress().getLine1();
			city = order.getAddress().getCity();
			pincode = order.getAddress().getPincode();
			latitude = order.getAddress().getLatitude(); // add if Address entity has it
			longitude = order.getAddress().getLongitude(); // add if Address entity has it
		}

		// ✅ Fetch items directly — avoids lazy collection issues
		List<OrderItemResponseDto> itemDtos = orderItemRepository.findByOrder_Id(order.getId()).stream()
				.map(item -> OrderItemResponseDto.builder().itemId(item.getId()).productId(item.getProduct().getId())
						.productName(item.getProduct().getName()).quantity(item.getQuantity())
						.unitPrice(item.getUnitPrice()).lineTotal(item.getLineTotal())
						.status(item.getItemStatus() != null ? item.getItemStatus().name() : null).build())
				.toList();

		return OrderNotificationDto.builder().orderId(order.getId()).orderNumber(order.getOrderNumber())
				.status(order.getStatus().name()).customerName(customerName).customerPhone(customerPhone)
				.deliveryAddress(addressLine).deliveryCity(city).deliveryPincode(pincode).latitude(latitude) // FIX:
																												// added
				.longitude(longitude) // FIX: added
				.subtotal(order.getSubtotal()).taxAmount(order.getTaxAmount()).deliveryFee(order.getDeliveryFee())
				.totalAmount(order.getTotalAmount()).placedAt(order.getPlacedAt()).confirmedAt(order.getConfirmedAt())
				.items(itemDtos).build();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// All your existing methods below — unchanged
	// ─────────────────────────────────────────────────────────────────────────

	@Transactional
	@Override
	public DeliveryAgentResponse createAgent(UUID userId, CreateAgentDto dto, UUID actorId) {

		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		deliveryAgentRepository.findByUserId(userId).ifPresent(agent -> {
			throw new RuntimeException("Delivery agent already exists for this user");
		});

		VehicleType vehicleType;
		try {
			vehicleType = VehicleType.valueOf(dto.getVehicleType().toUpperCase());
		} catch (Exception e) {
			throw new RuntimeException("Invalid vehicle type");
		}

		DeliveryAgent agent = new DeliveryAgent();
		agent.setUserId(userId);
		agent.setVehicleType(vehicleType);
		agent.setVehicleNumber(dto.getVehicleNumber());
		agent.setLicenseNumber(dto.getLicenseNumber());
		agent.setAvailabilityStatus(AvailabilityStatus.OFFLINE);
		agent.setVerified(false);
		agent.setUpdatedBy(actorId);

		DeliveryAgent savedAgent = deliveryAgentRepository.save(agent);

		return DeliveryAgentResponse.builder().id(savedAgent.getId()).userId(savedAgent.getUserId())
				.vehicleType(savedAgent.getVehicleType().name()).vehicleNumber(savedAgent.getVehicleNumber())
				.licenseNumber(savedAgent.getLicenseNumber())
				.availabilityStatus(savedAgent.getAvailabilityStatus().name()).isVerified(savedAgent.isVerified())
				.build();
	}

	@Override
	public DeliveryAgentResponse getAgent(UUID agentId) {

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

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

		DeliveryAgent agent = deliveryAgentRepository.findByUserId(userId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found for this user"));

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

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		if (dto.getVehicleType() != null) {
			VehicleType vehicleType;
			try {
				vehicleType = VehicleType.valueOf(dto.getVehicleType().toUpperCase());
			} catch (Exception e) {
				throw new RuntimeException("Invalid vehicle type");
			}
			agent.setVehicleType(vehicleType);
		}

		if (dto.getVehicleNumber() != null && !dto.getVehicleNumber().isBlank()) {
			agent.setVehicleNumber(dto.getVehicleNumber());
		}

		if (dto.getLicenseNumber() != null && !dto.getLicenseNumber().isBlank()) {
			agent.setLicenseNumber(dto.getLicenseNumber());
		}

		agent.setUpdatedBy(actorId);

		DeliveryAgent updatedAgent = deliveryAgentRepository.save(agent);

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

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		if (status == null) {
			throw new RuntimeException("Invalid availability status");
		}

		agent.setAvailabilityStatus(status);
		agent.setUpdatedBy(actorId);
		deliveryAgentRepository.save(agent);
	}

	@Transactional
	@Override
	public void verifyAgent(UUID agentId, UUID actorId) {

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		if (agent.isVerified()) {
			throw new RuntimeException("Agent is already verified");
		}

		agent.setVerified(true);
		agent.setUpdatedBy(actorId);
		deliveryAgentRepository.save(agent);
	}

	@Transactional
	@Override
	public void suspendAgent(UUID agentId, UUID actorId) {

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		if (agent.getAvailabilityStatus() == AvailabilityStatus.SUSPENDED) {
			throw new RuntimeException("Agent already suspended");
		}

		agent.setAvailabilityStatus(AvailabilityStatus.SUSPENDED);
		agent.setUpdatedBy(actorId);
		agent.setUpdatedAt(LocalDateTime.now());
		deliveryAgentRepository.save(agent);
	}

	@Transactional
	@Override
	public void softDeleteAgent(UUID agentId, UUID actorId) {

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		if (agent.isDeleted()) {
			throw new RuntimeException("Delivery agent already deleted");
		}

		agent.setDeleted(true);
		agent.setDeletedAt(LocalDateTime.now());
		agent.setDeletedBy(actorId);
		agent.setUpdatedBy(actorId);
		deliveryAgentRepository.save(agent);
	}

	@Override
	public List<DeliveryAgentResponse> listAvailableAgents() {

		List<DeliveryAgent> agents = deliveryAgentRepository.findAvailable();

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

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.DISPATCHED) {
			throw new RuntimeException("Order is not ready for delivery assignment");
		}

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		if (agent.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
			throw new RuntimeException("Delivery agent is not available");
		}

		if (!agent.isVerified()) {
			throw new RuntimeException("Delivery agent is not verified");
		}

		Optional<DeliveryAssignment> existingAssignment = deliveryAssignmentRepository.findByOrderId(orderId);
		if (existingAssignment.isPresent()) {
			throw new RuntimeException("Order already assigned to a delivery agent");
		}

		DeliveryAssignment assignment = new DeliveryAssignment();
		assignment.setOrderId(orderId);
		assignment.setAgentId(agentId);
		assignment.setStatus(DeliveryStatus.ASSIGNED);
		assignment.setAttemptNumber(1);
		assignment.setAssignedBy(actorId);
		assignment.setAssignedAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);

		agent.setAvailabilityStatus(AvailabilityStatus.ON_DELIVERY);
		agent.setUpdatedBy(actorId);
		deliveryAgentRepository.save(agent);

		order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
		orderRepository.save(order);
	}

	@Transactional
	@Override
	public void acceptAssignment(UUID assignmentId, UUID agentId) {

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		if (assignment.getStatus() != DeliveryStatus.ASSIGNED) {
			throw new RuntimeException("Only assigned deliveries can be accepted");
		}

		assignment.setStatus(DeliveryStatus.ACCEPTED);
		assignment.setAcceptedAt(LocalDateTime.now());
		assignment.setUpdatedAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void rejectAssignment(UUID assignmentId, UUID agentId, String reason) {

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		if (assignment.getStatus() == DeliveryStatus.REJECTED) {
			throw new RuntimeException("Assignment already rejected");
		}

		assignment.setStatus(DeliveryStatus.REJECTED);
		assignment.setRejectedAt(LocalDateTime.now());
		assignment.setFailureReason(reason);
		assignment.setUpdatedAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void markPickedUp(UUID assignmentId, UUID agentId) {

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		if (assignment.getStatus() != DeliveryStatus.ASSIGNED && assignment.getStatus() != DeliveryStatus.ACCEPTED) {
			throw new RuntimeException("Pickup not allowed at current stage");
		}

		if (assignment.getPickedUpAt() != null) {
			throw new RuntimeException("Order already marked as picked up");
		}

		assignment.setStatus(DeliveryStatus.PICKED_UP);
		assignment.setPickedUpAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void markInTransit(UUID assignmentId, UUID agentId) {

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		if (assignment.getStatus() != DeliveryStatus.PICKED_UP) {
			throw new RuntimeException("Delivery can be marked in transit only after pickup");
		}

		assignment.setStatus(DeliveryStatus.IN_TRANSIT);
		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void markDelivered(UUID assignmentId, UUID agentId, String proofUrl) {

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this delivery");
		}

		if (assignment.getStatus() != DeliveryStatus.PICKED_UP && assignment.getStatus() != DeliveryStatus.IN_TRANSIT) {
			throw new RuntimeException("Delivery cannot be marked as delivered at this stage");
		}

		assignment.setStatus(DeliveryStatus.DELIVERED);
		assignment.setDeliveryProofUrl(proofUrl);
		assignment.setDeliveredAt(LocalDateTime.now());
		assignment.setUpdatedAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);

		DeliveryAgent agent = deliveryAgentRepository.findById(agentId)
				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		agent.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
		deliveryAgentRepository.save(agent);

		orderService.markDelivered(assignment.getOrderId(), agentId);
	}

	@Transactional
	@Override
	public void markFailed(UUID assignmentId, UUID agentId, String reason) {

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		if (assignment.getStatus() == DeliveryStatus.DELIVERED) {
			throw new RuntimeException("Cannot mark a delivered assignment as failed");
		}

		if (assignment.getStatus() == DeliveryStatus.REJECTED) {
			throw new RuntimeException("Assignment already rejected");
		}

		assignment.setStatus(DeliveryStatus.FAILED);
		assignment.setFailureReason(reason);
		assignment.setUpdatedAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);
	}

	@Override
	public DeliveryAssignmentResponse getAssignment(UUID assignmentId) {

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

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

		DeliveryAssignment assignment = deliveryAssignmentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found for this order"));

		return DeliveryAssignmentResponse.builder().id(assignment.getId()).orderId(assignment.getOrderId())
				.agentId(assignment.getAgentId()).status(assignment.getStatus().name())
				.attemptNumber(assignment.getAttemptNumber()).deliveryProofUrl(assignment.getDeliveryProofUrl())
				.failureReason(assignment.getFailureReason()).assignedAt(assignment.getAssignedAt())
				.acceptedAt(assignment.getAcceptedAt()).rejectedAt(assignment.getRejectedAt())
				.pickedUpAt(assignment.getPickedUpAt()).deliveredAt(assignment.getDeliveredAt()).build();
	}

	@Override
	public List<DeliveryAssignmentResponse> listAssignmentsByAgent(UUID agentId, DeliveryAssignmentFilter filter) {

		deliveryAgentRepository.findById(agentId).orElseThrow(() -> new RuntimeException("Delivery agent not found"));

		List<DeliveryAssignment> assignments = deliveryAssignmentRepository.findByAgentId(agentId);

		return assignments.stream().filter(a -> {
			if (filter.getStatus() != null && !filter.getStatus().isEmpty()) {
				if (!a.getStatus().name().equalsIgnoreCase(filter.getStatus()))
					return false;
			}
			if (filter.getFromDate() != null) {
				if (a.getAssignedAt() == null || a.getAssignedAt().toLocalDate().isBefore(filter.getFromDate()))
					return false;
			}
			if (filter.getToDate() != null) {
				if (a.getAssignedAt() == null || a.getAssignedAt().toLocalDate().isAfter(filter.getToDate()))
					return false;
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

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));

		if (!assignment.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this assignment");
		}

		if (assignment.getStatus() != DeliveryStatus.ASSIGNED && assignment.getStatus() != DeliveryStatus.ACCEPTED) {
			throw new RuntimeException("Ride can only be started for assigned/accepted deliveries");
		}

		deliveryRideRepository.findByAssignmentId(assignmentId).ifPresent(r -> {
			throw new RuntimeException("Ride already started for this assignment");
		});

		DeliveryRide ride = new DeliveryRide();
		ride.setAssignmentId(assignmentId);
		ride.setAgentId(agentId);
		ride.setOrderId(assignment.getOrderId());
		ride.setStartLatitude(startLat);
		ride.setStartLongitude(startLng);
		ride.setStatus(RideStatus.STARTED);
		ride.setRideStartedAt(LocalDateTime.now());
		deliveryRideRepository.save(ride);

		assignment.setStatus(DeliveryStatus.IN_TRANSIT);
		assignment.setPickedUpAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void endRide(UUID rideId, UUID agentId, Double endLat, Double endLng, Double distanceKm) {

		DeliveryRide ride = deliveryRideRepository.findById(rideId)
				.orElseThrow(() -> new RuntimeException("Ride not found"));

		if (!ride.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized for this ride");
		}

		if (ride.getStatus() != RideStatus.STARTED) {
			throw new RuntimeException("Ride cannot be ended at this stage");
		}

		ride.setEndLatitude(endLat);
		ride.setEndLongitude(endLng);
		ride.setDistanceKm(distanceKm);
		ride.setRideEndedAt(LocalDateTime.now());

		float totalFare = ride.getBaseAmount() + ride.getKmAmount() + ride.getSurgeAmount();
		ride.setTotalFare(totalFare);
		ride.setStatus(RideStatus.COMPLETED);
		deliveryRideRepository.save(ride);

		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(ride.getAssignmentId())
				.orElseThrow(() -> new RuntimeException("Assignment not found"));

		assignment.setStatus(DeliveryStatus.DELIVERED);
		assignment.setDeliveredAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);
	}

	@Transactional
	@Override
	public void cancelRide(UUID rideId, UUID agentId) {

		DeliveryRide ride = deliveryRideRepository.findById(rideId)
				.orElseThrow(() -> new RuntimeException("Ride not found"));

		if (!ride.getAgentId().equals(agentId)) {
			throw new RuntimeException("Agent not authorized to cancel this ride");
		}

		if (ride.getStatus() == RideStatus.CANCELLED) {
			throw new RuntimeException("Ride already cancelled");
		}

		if (ride.getStatus() == RideStatus.COMPLETED) {
			throw new RuntimeException("Cannot cancel a completed ride");
		}

		ride.setStatus(RideStatus.CANCELLED);
		ride.setUpdatedAt(LocalDateTime.now());
		deliveryRideRepository.save(ride);
	}

	@Override
	public DeliveryRide getRide(UUID rideId) {

		return deliveryRideRepository.findById(rideId).orElseThrow(() -> new RuntimeException("Ride not found"));
	}

	@Transactional
	@Override
	public DeliveryPricingConfigResponse createPricingConfig(CreatePricingConfigDto dto, UUID actorId) {

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

		DeliveryPricingConfig saved = deliveryPricingConfigRepository.save(config);

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

		DeliveryPricingConfig config = configs.get(0);

		return DeliveryPricingConfigResponse.builder().id(config.getId()).configName(config.getConfigName())
				.basePrice(config.getBasePrice()).pricePerKm(config.getPricePerKm())
				.minDistanceKm(config.getMinDistanceKm()).surgeMultiplier(config.getSurgeMultiplier())
				.isActive(config.getIsActive()).description(config.getDescription())
				.effectiveFrom(config.getEffectiveFrom()).effectiveTo(config.getEffectiveTo()).build();
	}

	@Transactional
	@Override
	public DeliveryPricingConfigResponse updatePricingConfig(UUID configId, CreatePricingConfigDto dto, UUID actorId) {

		DeliveryPricingConfig config = deliveryPricingConfigRepository.findById(configId)
				.orElseThrow(() -> new RuntimeException("Pricing config not found with id: " + configId));

		if (dto.getConfigName() != null)
			config.setConfigName(dto.getConfigName());
		if (dto.getBasePrice() != null)
			config.setBasePrice(dto.getBasePrice());
		if (dto.getPricePerKm() != null)
			config.setPricePerKm(dto.getPricePerKm());
		if (dto.getMinDistanceKm() != null)
			config.setMinDistanceKm(dto.getMinDistanceKm());
		if (dto.getSurgeMultiplier() != null)
			config.setSurgeMultiplier(dto.getSurgeMultiplier());
		if (dto.getIsActive() != null)
			config.setIsActive(dto.getIsActive());
		if (dto.getDescription() != null)
			config.setDescription(dto.getDescription());
		if (dto.getEffectiveFrom() != null)
			config.setEffectiveFrom(dto.getEffectiveFrom());
		if (dto.getEffectiveTo() != null)
			config.setEffectiveTo(dto.getEffectiveTo());

		config.setUpdatedBy(actorId);

		DeliveryPricingConfig updated = deliveryPricingConfigRepository.save(config);

		return DeliveryPricingConfigResponse.builder().id(updated.getId()).configName(updated.getConfigName())
				.basePrice(updated.getBasePrice()).pricePerKm(updated.getPricePerKm())
				.minDistanceKm(updated.getMinDistanceKm()).surgeMultiplier(updated.getSurgeMultiplier())
				.isActive(updated.getIsActive()).description(updated.getDescription())
				.effectiveFrom(updated.getEffectiveFrom()).effectiveTo(updated.getEffectiveTo()).build();
	}

	@Transactional
	@Override
	public void deactivatePricingConfig(UUID configId, UUID actorId) {

		deliveryPricingConfigRepository.findById(configId)
				.orElseThrow(() -> new RuntimeException("Pricing config not found"));

		deliveryPricingConfigRepository.deactivateById(configId, actorId);
	}

	@Override
	public List<DeliveryPricingConfigResponse> listPricingConfigs() {

		return deliveryPricingConfigRepository.findAll().stream()
				.map(config -> DeliveryPricingConfigResponse.builder().id(config.getId())
						.configName(config.getConfigName()).basePrice(config.getBasePrice())
						.pricePerKm(config.getPricePerKm()).minDistanceKm(config.getMinDistanceKm())
						.surgeMultiplier(config.getSurgeMultiplier()).isActive(config.getIsActive())
						.description(config.getDescription()).effectiveFrom(config.getEffectiveFrom())
						.effectiveTo(config.getEffectiveTo()).build())
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<OrderNotificationDto> getAvailableOrders(UUID agentId) {
	    try {
	        List<Order> orders = new java.util.ArrayList<>();
	        orders.addAll(orderRepository.findByStatusWithDetails(OrderStatus.CONFIRMED));
	        orders.addAll(orderRepository.findByStatusWithDetails(OrderStatus.PROCESSING));

	        List<UUID> assignedIds = deliveryAssignmentRepository.findActiveAssignedOrderIds();

	        return orders.stream()
	                .filter(order -> !assignedIds.contains(order.getId()))
	                .map(this::mapOrderToNotificationDto)
	                .filter(Objects::nonNull)
	                .toList();

	    } catch (Exception e) {
	        log.error("Error loading available orders for agent {}", agentId, e);
	        return List.of();
	    }
	}
	
	// =========================================================================
	// ACCEPT ORDER → PROCESSING → DISPATCHED
	// =========================================================================

	@Override
	@Transactional
	public void acceptOrder(UUID orderId, UUID agentId) {

	    Order order = orderRepository.findById(orderId)
	            .orElseThrow(() -> new RuntimeException("Order not found"));

	    if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
	        throw new RuntimeException("Order is not available for pickup. Current status: " + order.getStatus());
	    }

	    if (deliveryAssignmentRepository.existsByOrderIdAndCompletedAtIsNull(orderId)) {
	        throw new RuntimeException("This order has already been accepted by another agent");
	    }

	    // If still CONFIRMED, move it to PROCESSING first
	    if (order.getStatus() == OrderStatus.CONFIRMED) {
	        orderService.markProcessing(orderId, agentId);
	    }

	    DeliveryAssignment assignment = new DeliveryAssignment();
	    assignment.setOrderId(orderId);
	    assignment.setAgentId(agentId);
	    assignment.setAcceptedAt(LocalDateTime.now());
	    deliveryAssignmentRepository.save(assignment);

	    // Now move PROCESSING -> DISPATCHED
	    orderService.markDispatched(orderId, agentId);

	    otpService.generateAndSendOtp(orderId);

	    log.info("[Delivery] Order {} accepted by agent {} -> DISPATCHED", orderId, agentId);
	}

	// =========================================================================
	// START DELIVERY → DISPATCHED → OUT_FOR_DELIVERY
	// =========================================================================

	@Override
	@Transactional
	public void startDelivery(UUID orderId, UUID agentId) {

		validateAgentOwnsOrder(orderId, agentId);

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.DISPATCHED) {
			throw new RuntimeException("Cannot start delivery. Order status is: " + order.getStatus());
		}

		// Transition: DISPATCHED → OUT_FOR_DELIVERY
		orderService.markOutForDelivery(orderId, agentId);

		log.info("[Delivery] Order {} started by agent {} → OUT_FOR_DELIVERY", orderId, agentId);
	}

	// =========================================================================
	// COMPLETE DELIVERY (OTP) → OUT_FOR_DELIVERY → DELIVERED
	// =========================================================================

	@Override
	@Transactional
	public void completeDelivery(UUID orderId, UUID agentId, String otp) {

		validateAgentOwnsOrder(orderId, agentId);

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
			throw new RuntimeException("Cannot complete delivery. Order status is: " + order.getStatus());
		}

		// ── Validate OTP ─────────────────────────────────────────────────────
		boolean otpValid = otpService.validateOtp(orderId, otp);
		if (!otpValid) {
			throw new RuntimeException("Invalid OTP. Please ask the customer for the correct code.");
		}

		// Transition: OUT_FOR_DELIVERY → DELIVERED
		orderService.markDelivered(orderId, agentId);

		// Mark assignment as complete
		DeliveryAssignment assignment = deliveryAssignmentRepository.findByOrderIdAndCompletedAtIsNull(orderId)
				.orElseThrow(() -> new RuntimeException("Assignment not found"));
		assignment.setCompletedAt(LocalDateTime.now());
		deliveryAssignmentRepository.save(assignment);

		// Invalidate used OTP so it can't be reused
		otpService.invalidateOtp(orderId);

		log.info("[Delivery] Order {} delivered by agent {} → DELIVERED", orderId, agentId);
	}

	// =========================================================================
	// MY ORDERS — orders assigned to this agent
	// =========================================================================

	@Override
	public List<OrderNotificationDto> getMyOrders(UUID agentId) {
	    System.out.println("=== [DEBUG-MYORDERS] getMyOrders() called for agent: " + agentId);

	    try {
	        List<UUID> myOrderIds = deliveryAssignmentRepository.findOrderIdsByAgentId(agentId);
	        System.out.println("=== [DEBUG-MYORDERS] Agent has " + myOrderIds.size() + " assigned orders");

	        List<Order> orders = orderRepository.findAllById(myOrderIds);

	        List<OrderNotificationDto> result = orders.stream()
	                .map(this::mapOrderToNotificationDto)
	                .filter(Objects::nonNull)
	                .collect(Collectors.toList());

	        System.out.println("=== [DEBUG-MYORDERS] SUCCESS - Returning " + result.size() + " orders");
	        return result;

	    } catch (Exception e) {
	        System.err.println("=== [ERROR-MYORDERS] Exception in getMyOrders: " + e.getMessage());
	        e.printStackTrace();
	        return List.of();
	    }
	}

	// =========================================================================
	// LOCATION
	// =========================================================================

	@Override
	public void updateAgentLocation(UUID orderId, UUID agentId, double latitude, double longitude) {
		// Store in Redis / push via WebSocket to customer — implement per your infra.
		// Minimal implementation: just log. Replace with your tracking store.
		log.debug("[Location] Agent {} → Order {} @ ({}, {})", agentId, orderId, latitude, longitude);
		// Example Redis call (uncomment if you have Redis):
		// redisTemplate.opsForValue().set("location:" + orderId, latitude + "," +
		// longitude, 10, TimeUnit.MINUTES);
	}

	@Override
	public void stopAgentLocation(UUID orderId, UUID agentId) {
		log.debug("[Location] Agent {} stopped sharing location for order {}", agentId, orderId);
		// Example Redis cleanup:
		// redisTemplate.delete("location:" + orderId);
	}

	// =========================================================================
	// HELPERS
	// =========================================================================

	/**
	 * Ensures the calling agent actually owns this order's assignment. Prevents one
	 * agent from completing another agent's delivery.
	 */
	private void validateAgentOwnsOrder(UUID orderId, UUID agentId) {
		boolean owns = deliveryAssignmentRepository.existsByOrderIdAndAgentIdAndCompletedAtIsNull(orderId, agentId);
		if (!owns) {
			throw new RuntimeException("You are not assigned to this order");
		}
	}

	private OrderNotificationDto toNotificationDto(Order order) {
	    System.out.println("=== [DEBUG-MAPPER] Mapping order: " + order.getId());

	    try {
	        String customerName = (order.getUser() != null) ? order.getUser().getFullName() : "Unknown";
	        String addressLine = (order.getAddress() != null) ? order.getAddress().getLine1() : null;
	        String city = (order.getAddress() != null) ? order.getAddress().getCity() : null;

	        List<OrderItemResponseDto> itemDtos = orderItemRepository.findByOrder_Id(order.getId()).stream()
	                .map(item -> OrderItemResponseDto.builder()
	                        .productName(item.getProduct() != null ? item.getProduct().getName() : "N/A")
	                        .quantity(item.getQuantity())
	                        .build())
	                .collect(Collectors.toList());

	        OrderNotificationDto dto = OrderNotificationDto.builder()
	                .orderId(order.getId())
	                .orderNumber(order.getOrderNumber())
	                .status(order.getStatus().name())
	                .customerName(customerName)
	                .deliveryAddress(addressLine)
	                .deliveryCity(city)
	                .totalAmount(order.getTotalAmount())
	                .placedAt(order.getPlacedAt())
	                .items(itemDtos)
	                .build();

	        System.out.println("=== [DEBUG-MAPPER] SUCCESS mapped order " + order.getId());
	        return dto;

	    } catch (Exception e) {
	        System.err.println("=== [DEBUG-MAPPER] FAILED to map order " + order.getId() + " → " + e.getMessage());
	        e.printStackTrace();
	        return null;
	    }
	}

}