package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class UpdateCategoryDto {

    private String name;

    private String imageUrl;

    private Boolean isActive;

    private Integer sortOrder;

    private UUID parentId;
}
