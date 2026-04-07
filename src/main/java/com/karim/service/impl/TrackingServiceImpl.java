//package com.karim.service.impl;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import com.karim.dto.AgentLocationPingResponse;
//import com.karim.dto.LocationPingDto;
//import com.karim.dto.LocationPingResponse;
//import com.karim.dto.TrackingSessionResponse;
//import com.karim.entity.AgentLocationPing;
//import com.karim.entity.DeliveryAssignment;
//import com.karim.entity.DeliveryRide;
//import com.karim.entity.TrackingSession;
//import com.karim.enums.DeliveryStatus;
//import com.karim.repository.AgentLocationPingRepository;
//import com.karim.repository.DeliveryAssignmentRepository;
//import com.karim.repository.DeliveryRideRepository;
//import com.karim.repository.TrackingSessionRepository;
//import com.karim.service.TrackingService;
//
//@Service
//public class TrackingServiceImpl implements TrackingService {
//
//	private final TrackingSessionRepository trackingSessionRepository;
//	private final DeliveryAssignmentRepository deliveryAssignmentRepository;
//	private final AgentLocationPingRepository agentLocationPingRepository;
//	private final DeliveryRideRepository deliveryRideRepository;
//
//	public TrackingServiceImpl(TrackingSessionRepository trackingSessionRepository,
//			DeliveryAssignmentRepository deliveryAssignmentRepository,
//			AgentLocationPingRepository agentLocationPingRepository, DeliveryRideRepository deliveryRideRepository) {
//		this.trackingSessionRepository = trackingSessionRepository;
//		this.deliveryAssignmentRepository = deliveryAssignmentRepository;
//		this.agentLocationPingRepository = agentLocationPingRepository;
//		this.deliveryRideRepository = deliveryRideRepository;
//	}
//
//	@Transactional
//	@Override
//	public TrackingSessionResponse createTrackingSession(UUID assignmentId, UUID userId) {
//
//		// 🔍 1. Validate assignment
//		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
//				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));
//
//		// ⚠️ 2. Validate user access (order owner / customer)
//		if (!assignment.getOrderId().equals(userId)) {
//			throw new RuntimeException("User not authorized for this tracking session");
//		}
//
//		// 🔍 3. Prevent duplicate active session
//		Optional<TrackingSession> existingSession = trackingSessionRepository
//				.findByAssignmentIdAndUserIdAndIsActiveTrue(assignmentId, userId);
//
//		if (existingSession.isPresent()) {
//			return TrackingSessionResponse.builder().id(existingSession.get().getId())
//					.wsToken(existingSession.get().getWsToken()).assignmentId(assignmentId).userId(userId)
//					.isActive(true).expiresAt(existingSession.get().getExpiresAt()).build();
//		}
//
//		// 🔐 4. Generate WS token
//		String wsToken = UUID.randomUUID().toString();
//
//		// 🧾 5. Create tracking session
//		TrackingSession session = new TrackingSession();
//		session.setAssignmentId(assignmentId);
//		session.setUserId(userId);
//		session.setWsToken(wsToken);
//		session.setActive(true);
//		session.setExpiresAt(LocalDateTime.now().plusHours(1)); // configurable later
//
//		trackingSessionRepository.save(session);
//
//		// 📦 6. Return response
//		return TrackingSessionResponse.builder().id(session.getId()).wsToken(session.getWsToken())
//				.assignmentId(session.getAssignmentId()).userId(session.getUserId()).isActive(session.isActive())
//				.expiresAt(session.getExpiresAt()).build();
//	}
//
//	@Transactional(readOnly = true)
//	@Override
//	public TrackingSession validateTrackingSession(String wsToken) {
//
//		// 🔍 1. Fetch session by token
//		TrackingSession session = trackingSessionRepository.findByWsTokenAndIsActiveTrue(wsToken)
//				.orElseThrow(() -> new RuntimeException("Invalid or inactive tracking session"));
//
//		// ⏰ 2. Check expiry
//		if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
//
//			// Optional: deactivate expired session
//			session.setActive(false);
//			trackingSessionRepository.save(session);
//
//			throw new RuntimeException("Tracking session expired");
//		}
//
//		return session;
//	}
//
//	@Transactional
//	@Override
//	public void refreshSession(UUID sessionId) {
//
//		// 🔍 1. Fetch session
//		TrackingSession session = trackingSessionRepository.findById(sessionId)
//				.orElseThrow(() -> new RuntimeException("Tracking session not found"));
//
//		// ⚠️ 2. Validate session is active
//		if (!session.isActive()) {
//			throw new RuntimeException("Cannot refresh an inactive session");
//		}
//
//		// ⚠️ 3. Validate expiry (optional but recommended)
//		if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
//			throw new RuntimeException("Session already expired");
//		}
//
//		// 🔄 4. Extend expiry (example: extend by 1 hour)
//		LocalDateTime newExpiry = LocalDateTime.now().plusHours(1);
//		session.setExpiresAt(newExpiry);
//
//		// 🕒 5. Update last ping time (optional but useful)
//		session.setLastPingAt(LocalDateTime.now());
//
//		trackingSessionRepository.save(session);
//	}
//
//	@Transactional
//	@Override
//	public void closeSession(UUID sessionId) {
//
//		// 🔍 1. Fetch session
//		TrackingSession session = trackingSessionRepository.findById(sessionId)
//				.orElseThrow(() -> new RuntimeException("Tracking session not found"));
//
//		// ⚠️ 2. Check if already closed
//		if (!session.isActive()) {
//			throw new RuntimeException("Tracking session is already closed");
//		}
//
//		// 🔄 3. Update session
//		session.setActive(false);
//		session.setClosedAt(LocalDateTime.now());
//
//		trackingSessionRepository.save(session);
//	}
//
//	@Transactional
//	@Override
//	public void recordLocationPing(UUID assignmentId, UUID agentId, LocationPingDto dto) {
//
//		// 🔍 1. Validate assignment
//		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
//				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));
//
//		// ⚠️ 2. Validate agent ownership
//		if (!assignment.getAgentId().equals(agentId)) {
//			throw new RuntimeException("Agent not authorized for this assignment");
//		}
//
//		// 🔍 3. Optional: ensure ride exists
//		DeliveryRide ride = deliveryRideRepository.findByAssignmentId(assignmentId)
//				.orElseThrow(() -> new RuntimeException("Ride not started for this assignment"));
//
//		// 🔢 4. Validate sequence ordering (optional but recommended)
//		// You can enforce monotonic sequence if required
//		Long lastSequence = agentLocationPingRepository.findTopByAssignmentIdOrderByPingSequenceDesc(assignmentId)
//				.map(AgentLocationPing::getPingSequence).orElse(0L);
//
//		if (dto.getPingSequence() <= lastSequence) {
//			throw new RuntimeException("Invalid ping sequence. Must be greater than last sequence");
//		}
//
//		// 🧾 5. Create ping record
//		AgentLocationPing ping = new AgentLocationPing();
//		ping.setAssignmentId(assignmentId);
//		ping.setAgentId(agentId);
//		ping.setPingSequence(dto.getPingSequence());
//		ping.setLatitude(dto.getLatitude());
//		ping.setLongitude(dto.getLongitude());
//		ping.setAccuracyMeters(dto.getAccuracyMeters());
//		ping.setSpeedKmh(dto.getSpeedKmh());
//		ping.setBearing(dto.getBearing());
//		ping.setAltitudeMeters(dto.getAltitudeMeters());
//		ping.setEventType(dto.getEventType());
//		ping.setWsConnectionId(dto.getWsConnectionId());
//		ping.setCreatedAt(LocalDateTime.now());
//
//		agentLocationPingRepository.save(ping);
//
//		// 🔄 6. (Optional but useful) Update ride / session last ping
//		ride.setEndLatitude(dto.getLatitude());
//		ride.setEndLongitude(dto.getLongitude());
//		ride.setUpdatedAt(LocalDateTime.now());
//		deliveryRideRepository.save(ride);
//	}
//
//	@Override
//	public AgentLocationPingResponse getLatestPing(UUID assignmentId) {
//
//		// 🔍 1. Validate assignment exists
//		deliveryAssignmentRepository.findById(assignmentId)
//				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));
//
//		// 🔍 2. Fetch latest ping
//		AgentLocationPing ping = agentLocationPingRepository.findTopByAssignmentIdOrderByPingSequenceDesc(assignmentId)
//				.orElseThrow(() -> new RuntimeException("No location ping found for this assignment"));
//
//		// 📦 3. Map to response DTO
//		return AgentLocationPingResponse.builder().id(ping.getId()).assignmentId(ping.getAssignmentId())
//				.agentId(ping.getAgentId()).pingSequence(ping.getPingSequence()).latitude(ping.getLatitude())
//				.longitude(ping.getLongitude()).accuracyMeters(ping.getAccuracyMeters()).speedKmh(ping.getSpeedKmh())
//				.bearing(ping.getBearing()).altitudeMeters(ping.getAltitudeMeters()).eventType(ping.getEventType())
//				.wsConnectionId(ping.getWsConnectionId()).createdAt(ping.getCreatedAt()).build();
//	}
//
//	@Override
//	public List<LocationPingResponse> getPingHistory(UUID assignmentId, LocalDateTime from, LocalDateTime to) {
//
//		// 🔍 1. Validate assignment exists
//		deliveryAssignmentRepository.findById(assignmentId)
//				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));
//
//		// 📊 2. Fetch ping history
//		List<AgentLocationPing> pings = agentLocationPingRepository.findPingHistory(assignmentId, from, to);
//
//		// 🔄 3. Map to response DTO
//		return pings.stream()
//				.map(p -> LocationPingResponse.builder().id(p.getId()).assignmentId(p.getAssignmentId())
//						.agentId(p.getAgentId()).pingSequence(p.getPingSequence()).latitude(p.getLatitude())
//						.longitude(p.getLongitude()).accuracyMeters(p.getAccuracyMeters()).speedKmh(p.getSpeedKmh())
//						.bearing(p.getBearing()).altitudeMeters(p.getAltitudeMeters()).eventType(p.getEventType())
//						.wsConnectionId(p.getWsConnectionId()).createdAt(p.getCreatedAt()).build())
//				.toList();
//	}
//
//	@Override
//	public Optional<TrackingSessionResponse> getActiveSessionForAssignment(UUID assignmentId) {
//
//		TrackingSession session = trackingSessionRepository.findByAssignmentIdAndIsActiveTrue(assignmentId)
//				.orElse(null);
//
//		if (session == null) {
//			return Optional.empty();
//		}
//
//		TrackingSessionResponse response = TrackingSessionResponse.builder().id(session.getId())
//				.assignmentId(session.getAssignmentId()).userId(session.getUserId()).wsToken(session.getWsToken())
//				.isActive(session.isActive()).expiresAt(session.getExpiresAt()).build();
//
//		return Optional.of(response);
//	}
//
//	@Transactional
//	@Override
//	public void softDeletePings(UUID assignmentId, UUID actorId) {
//
//		// 🔍 1. Validate assignment exists
//		DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
//				.orElseThrow(() -> new RuntimeException("Delivery assignment not found"));
//
//		// ⚠️ 2. Optional: ensure only completed/cancelled deliveries are cleaned up
//		if (assignment.getStatus() != DeliveryStatus.DELIVERED && assignment.getStatus() != DeliveryStatus.REJECTED) {
//			throw new RuntimeException("Pings can only be deleted after delivery is completed or cancelled");
//		}
//
//		// 🧹 3. Soft delete all pings for the assignment
//		agentLocationPingRepository.softDeleteByAssignmentId(assignmentId, actorId);
//	}
//}