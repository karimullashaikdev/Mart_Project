package com.karim.dto;

import java.util.List;
import java.util.UUID;

import com.karim.enums.UnitType;

import lombok.Data;

@Data
public class UpdateProductDto {

    private UUID categoryId;
    private String name;
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
}

