package com.karim.dto;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class CategoryTreeDto {

	private UUID id;
	private String name;
	private String slug;
	private List<CategoryTreeDto> children;

}
