package com.karim.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class CategoryResponseDto {

    private UUID id;
    private String name;
    private String slug;
    private String imageUrl;
    private Boolean isActive;
    private Integer sortOrder;
    private UUID parentId;

    
}
