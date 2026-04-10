package com.karim.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCategoryDto {

    @NotBlank
    private String name;

    private String imageUrl;

    private Boolean isActive;

    private Integer sortOrder;

    private UUID parentId; // optional (for tree structure)
}
