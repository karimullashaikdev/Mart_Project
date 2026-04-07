package com.karim.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAgentDto {

    @NotNull
    private UUID userId;

    @NotBlank
    private String vehicleType; // should map to enum VehicleType

    @NotBlank
    private String vehicleNumber;

    @NotBlank
    private String licenseNumber;
}
