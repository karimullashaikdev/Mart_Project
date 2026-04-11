package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class PlaceOrderRequestDto {
    private UUID addressId;
    private String customerNotes;
}