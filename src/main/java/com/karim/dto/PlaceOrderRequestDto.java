package com.karim.dto;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class PlaceOrderRequestDto {

    private UUID addressId;
    private String customerNotes;

    private List<OrderItemRequestDto> items;
}