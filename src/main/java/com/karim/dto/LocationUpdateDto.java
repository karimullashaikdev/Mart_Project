package com.karim.dto;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /api/delivery/orders/{orderId}/location
 * Agent's current GPS coordinates pushed periodically during delivery.
 */
@Getter
@Setter
public class LocationUpdateDto {

    private UUID orderId;
    private double latitude;
    private double longitude;
}