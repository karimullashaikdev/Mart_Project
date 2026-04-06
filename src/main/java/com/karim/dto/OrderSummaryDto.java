package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderSummaryDto {

    private UUID orderId;
    private String orderNumber;
    private String status;

    private Double totalAmount;
    private LocalDateTime placedAt;
}
