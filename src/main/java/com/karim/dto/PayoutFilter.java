package com.karim.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PayoutFilter {

	// Optional payout status filter (e.g., PENDING, PAID, FAILED)
	private String status;

	// Filter payouts created after this date
	private LocalDateTime fromDate;

	// Filter payouts created before this date
	private LocalDateTime toDate;

	// Optional: minimum payout amount
	private Float minAmount;

	// Optional: maximum payout amount
	private Float maxAmount;
}
