package com.karim.service;

import java.util.List;
import java.util.UUID;

import com.karim.dto.CategoryFilterDto;
import com.karim.dto.CategoryResponseDto;
import com.karim.dto.CategoryTreeDto;
import com.karim.dto.CreateCategoryDto;
import com.karim.dto.UpdateCategoryDto;

public interface CategoryService {

	CategoryResponseDto createCategory(CreateCategoryDto dto, UUID actorId);

	List<CategoryTreeDto> getCategoryTree();

	CategoryResponseDto getCategory(String categoryIdOrSlug);

	CategoryResponseDto updateCategory(UUID categoryId, UpdateCategoryDto dto, UUID actorId);
	
	 void reorderCategories(List<UUID> orderedIds, UUID actorId);
	 
	 void softDeleteCategory(UUID categoryId, UUID actorId);
	 
	 void restoreCategory(UUID categoryId, UUID actorId);
	 
	 List<CategoryResponseDto> listCategoriesAdmin(CategoryFilterDto filters);
}
