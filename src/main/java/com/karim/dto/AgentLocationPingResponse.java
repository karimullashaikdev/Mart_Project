package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.karim.enums.PingEventType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentLocationPingResponse {

    private UUID id;
    private UUID assignmentId;
    private UUID agentId;

    private Long pingSequence;

    private Double latitude;
    private Double longitude;

    private Double accuracyMeters;
    private Double speedKmh;
    private Double bearing;
    private Double altitudeMeters;

    private PingEventType eventType;
    private String wsConnectionId;

    private LocalDateTime createdAt;
}
