package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.karim.enums.StockTransactionType;

import lombok.Data;

@Data
public class StockTransactionFilter {

    private StockTransactionType type;
    private UUID orderId;
    private UUID returnRequestId;

    private LocalDateTime fromDate;
    private LocalDateTime toDate;
}
