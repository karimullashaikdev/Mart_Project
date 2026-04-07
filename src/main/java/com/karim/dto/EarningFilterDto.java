package com.karim.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class EarningFilterDto {

	private LocalDate fromDate;
	private LocalDate toDate;
	private String status; // PENDING, PAID, FAILED (optional)
}