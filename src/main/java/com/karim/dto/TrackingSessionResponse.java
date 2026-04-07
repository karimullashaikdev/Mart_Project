package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrackingSessionResponse {

    private UUID id;
    private UUID assignmentId;
    private UUID userId;
    private String wsToken;
    private boolean isActive;
    private LocalDateTime expiresAt;
}
