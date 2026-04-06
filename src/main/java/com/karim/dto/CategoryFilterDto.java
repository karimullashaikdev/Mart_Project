package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class CategoryFilterDto {

    private String name;

    private Boolean isActive;

    private Boolean isDeleted;

    private UUID parentId;
}

