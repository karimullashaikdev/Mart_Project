package com.karim.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.karim.dto.AgentLocationPingResponse;
import com.karim.dto.LocationPingDto;
import com.karim.dto.LocationPingResponse;
import com.karim.dto.TrackingSessionResponse;
import com.karim.entity.TrackingSession;

public interface TrackingService {

	TrackingSessionResponse createTrackingSession(UUID assignmentId, UUID userId);

	TrackingSession validateTrackingSession(String wsToken);

	void refreshSession(UUID sessionId);

	void closeSession(UUID sessionId);

	void recordLocationPing(UUID assignmentId, UUID agentId, LocationPingDto dto);

	AgentLocationPingResponse getLatestPing(UUID assignmentId);

	List<LocationPingResponse> getPingHistory(UUID assignmentId, LocalDateTime from, LocalDateTime to);

	Optional<TrackingSessionResponse> getActiveSessionForAssignment(UUID assignmentId);
	
	void softDeletePings(UUID assignmentId, UUID actorId);
}
