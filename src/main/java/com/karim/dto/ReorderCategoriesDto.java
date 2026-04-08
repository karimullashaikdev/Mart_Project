package com.karim.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReorderCategoriesDto {

	@NotNull(message = "Ordered IDs list cannot be null")
	@NotEmpty(message = "Ordered IDs list cannot be empty")
	private List<UUID> orderedIds;
}