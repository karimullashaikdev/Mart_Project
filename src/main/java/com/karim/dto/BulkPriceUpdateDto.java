package com.karim.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class BulkPriceUpdateDto {

    private UUID productId;
    private BigDecimal mrp;
    private BigDecimal sellingPrice;
}