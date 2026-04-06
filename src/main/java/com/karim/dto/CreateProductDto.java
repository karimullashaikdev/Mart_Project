package com.karim.dto;

import java.util.List;
import java.util.UUID;

import com.karim.enums.UnitType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProductDto {

	@NotBlank
	private String name;

	private String description;

	@NotBlank
	private String sku;

	private String barcode;

	private Double mrp;

	private Double sellingPrice;

	private Double taxPercent;

	@NotNull
	private UnitType unit;

	private Double unitValue;

	private List<String> images;

	private Boolean isActive;

	@NotNull
	private UUID categoryId;
}
