package com.karim.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class ProductFilterDto {

	private UUID categoryId;
	private Boolean isActive;
	private BigDecimal minPrice;
	private BigDecimal maxPrice;
	private String search; // name or sku
}