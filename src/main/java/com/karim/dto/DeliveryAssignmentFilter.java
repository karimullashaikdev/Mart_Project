package com.karim.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class DeliveryAssignmentFilter {

	private String status; // optional (ASSIGNED, DELIVERED, etc.)
	private LocalDate fromDate;
	private LocalDate toDate;
}
