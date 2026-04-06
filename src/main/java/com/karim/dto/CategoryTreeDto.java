package com.karim.dto;

import java.util.List;
import java.util.UUID;

public class CategoryTreeDto {

    private UUID id;
    private String name;
    private String slug;
    private List<CategoryTreeDto> children;

    // getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public List<CategoryTreeDto> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryTreeDto> children) {
        this.children = children;
    }
}
