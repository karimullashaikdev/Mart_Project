package com.karim.dto;

import java.util.UUID;

import com.karim.enums.PingEventType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocationPingDto {

    @NotNull
    private UUID assignmentId;

    @NotNull
    @Min(1)
    private Long pingSequence;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private Double accuracyMeters;

    private Double speedKmh;

    @Min(0)
    @Max(360)
    private Double bearing;

    private Double altitudeMeters;

    @NotNull
    private PingEventType eventType;

    private String wsConnectionId;
}