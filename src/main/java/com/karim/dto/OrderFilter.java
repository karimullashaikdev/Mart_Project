package com.karim.dto;

import java.time.LocalDateTime;

import com.karim.enums.OrderStatus;

import lombok.Data;

@Data
public class OrderFilter {

    private OrderStatus status;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
}
