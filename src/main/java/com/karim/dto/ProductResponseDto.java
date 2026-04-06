package com.karim.dto;

import java.util.List;
import java.util.UUID;

import com.karim.enums.UnitType;

import lombok.Data;

@Data
public class ProductResponseDto {

	private UUID id;
    private String name;
    private String slug;
    private String description;
    private String sku;
    private String barcode;

    private Double mrp;
    private Double sellingPrice;
    private Double taxPercent;

    private UnitType unit;
    private Double unitValue;

    private List<String> images;

    private Boolean isActive;

    private UUID categoryId;

    // Stock details
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Boolean inStock;
}
