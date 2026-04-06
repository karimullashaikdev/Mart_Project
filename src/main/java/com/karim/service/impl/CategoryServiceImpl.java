package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.CategoryFilterDto;
import com.karim.dto.CategoryResponseDto;
import com.karim.dto.CategoryTreeDto;
import com.karim.dto.CreateCategoryDto;
import com.karim.dto.UpdateCategoryDto;
import com.karim.entity.Category;
import com.karim.repository.CategoryRepository;
import com.karim.service.CategoryService;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

	private final CategoryRepository categoryRepository;

	public CategoryServiceImpl(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	// ----------------------------------------------------------------
	// CREATE
	// ----------------------------------------------------------------

	@Override
	public CategoryResponseDto createCategory(CreateCategoryDto dto, UUID actorId) {

		Category category = new Category();

		category.setName(dto.getName());
		category.setSlug(generateSlug(dto.getName()));
		category.setImageUrl(dto.getImageUrl());
		category.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

		if (dto.getParentId() != null) {
			Category parent = categoryRepository.findById(dto.getParentId())
					.orElseThrow(() -> new RuntimeException("Parent category not found"));
			category.setParent(parent);
		}

		// ✅ FIX: use count() instead of findAll().size() — avoids loading all rows
		Integer sortOrder = dto.getSortOrder();
		if (sortOrder == null) {
			sortOrder = (int) categoryRepository.count() + 1;
		}
		category.setSortOrder(sortOrder);

		category.setCreatedBy(actorId);

		Category saved = categoryRepository.save(category);
		return mapToResponse(saved);
	}

	// ----------------------------------------------------------------
	// READ — tree
	// ----------------------------------------------------------------

	@Override
	public List<CategoryTreeDto> getCategoryTree() {

		List<Category> categories = categoryRepository.findAllActive();

		Map<UUID, CategoryTreeDto> dtoMap = new HashMap<>();
		for (Category category : categories) {
			dtoMap.put(category.getId(), mapToTreeDto(category));
		}

		List<CategoryTreeDto> roots = new ArrayList<>();

		for (Category category : categories) {
			CategoryTreeDto dto = dtoMap.get(category.getId());

			if (category.getParent() != null) {
				CategoryTreeDto parentDto = dtoMap.get(category.getParent().getId());
				if (parentDto != null) {
					parentDto.getChildren().add(dto);
				}
			} else {
				roots.add(dto);
			}
		}

		return roots;
	}

	// ----------------------------------------------------------------
	// READ — single
	// ----------------------------------------------------------------

	@Override
	public CategoryResponseDto getCategory(String categoryIdOrSlug) {

		Category category;

		try {
			UUID id = UUID.fromString(categoryIdOrSlug);
			category = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
		} catch (IllegalArgumentException e) {
			// Not a UUID → treat as slug
			category = categoryRepository.findBySlug(categoryIdOrSlug)
					.orElseThrow(() -> new RuntimeException("Category not found"));
		}

		return mapToResponse(category);
	}

	// ----------------------------------------------------------------
	// UPDATE
	// ----------------------------------------------------------------

	@Override
	public CategoryResponseDto updateCategory(UUID categoryId, UpdateCategoryDto dto, UUID actorId) {

		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new RuntimeException("Category not found"));

		if (dto.getName() != null && !dto.getName().isBlank()) {
			category.setName(dto.getName());
			category.setSlug(generateSlug(dto.getName()));
		}

		if (dto.getImageUrl() != null) {
			category.setImageUrl(dto.getImageUrl());
		}

		if (dto.getIsActive() != null) {
			category.setIsActive(dto.getIsActive());
		}

		if (dto.getSortOrder() != null) {
			category.setSortOrder(dto.getSortOrder());
		}

		if (dto.getParentId() != null) {

			if (dto.getParentId().equals(categoryId)) {
				throw new RuntimeException("Category cannot be its own parent");
			}

			Category parent = categoryRepository.findById(dto.getParentId())
					.orElseThrow(() -> new RuntimeException("Parent category not found"));

			if (isCircular(parent, category)) {
				throw new RuntimeException("Invalid parent assignment (circular dependency)");
			}

			category.setParent(parent);
		}

		category.setUpdatedBy(actorId);

		Category updated = categoryRepository.save(category);
		return mapToResponse(updated);
	}

	@Override
	public void reorderCategories(List<UUID> orderedIds, UUID actorId) {

		if (orderedIds == null || orderedIds.isEmpty()) {
			throw new RuntimeException("Ordered category IDs cannot be empty");
		}

		int sortOrder = 1;

		for (UUID id : orderedIds) {

			Category category = categoryRepository.findById(id)
					.orElseThrow(() -> new RuntimeException("Category not found: " + id));

			category.setSortOrder(sortOrder++);
			category.setUpdatedBy(actorId);

			// Using bulk update method (optional optimization)
			// categoryRepository.updateSortOrder(id, sortOrder);
		}

		// Save all updated entities in batch
		// Since we're in @Transactional, dirty checking will flush automatically
	}

	@Override
	public void softDeleteCategory(UUID categoryId, UUID actorId) {

		// ✅ Fetch category
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new RuntimeException("Category not found"));

		// ❌ Check active products
		boolean hasActiveProducts = categoryRepository.hasActiveProducts(categoryId);
		if (hasActiveProducts) {
			throw new RuntimeException("Cannot delete category with active products");
		}

		// ❌ Check active child categories
		if (category.getChildren() != null && !category.getChildren().isEmpty()) {
			boolean hasActiveChildren = category.getChildren().stream()
					.anyMatch(child -> Boolean.TRUE.equals(child.getIsActive()) && !child.getIsDeleted());

			if (hasActiveChildren) {
				throw new RuntimeException("Cannot delete category with active child categories");
			}
		}

		// ✅ Soft delete
		category.setIsDeleted(true);
		category.setDeletedAt(LocalDateTime.now());
		category.setDeletedBy(actorId);

		categoryRepository.save(category);
	}

	@Override
	public void restoreCategory(UUID categoryId, UUID actorId) {

		// ✅ Fetch including soft-deleted records
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new RuntimeException("Category not found"));

		// ❌ Already active
		if (!Boolean.TRUE.equals(category.getIsDeleted())) {
			throw new RuntimeException("Category is not deleted");
		}

		// ⚠️ Optional: validate parent is not deleted
		if (category.getParent() != null && Boolean.TRUE.equals(category.getParent().getIsDeleted())) {
			throw new RuntimeException("Cannot restore category because parent category is deleted");
		}

		// ✅ Restore category
		category.setIsDeleted(false);
		category.setDeletedAt(null);
		category.setDeletedBy(null);

		// ✅ Audit
		category.setUpdatedBy(actorId);

		categoryRepository.save(category);
	}

	@Override
	public List<CategoryResponseDto> listCategoriesAdmin(CategoryFilterDto filters) {

		List<Category> categories = categoryRepository.findCategoriesAdmin(filters.getName(), filters.getIsActive(),
				filters.getIsDeleted(), filters.getParentId());

		return categories.stream().map(this::mapToResponse).collect(Collectors.toList());
	}

	// ----------------------------------------------------------------
	// Private helpers (each defined ONCE)
	// ----------------------------------------------------------------

	// ✅ FIX: was duplicated — now defined exactly once
	private String generateSlug(String name) {
		return name.toLowerCase().trim().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "-");
	}

	// ✅ FIX: mapToResponse + mapToResponseDTO were identical duplicates — unified
	// into one
	private CategoryResponseDto mapToResponse(Category category) {
		CategoryResponseDto dto = new CategoryResponseDto();

		dto.setId(category.getId());
		dto.setName(category.getName());
		dto.setSlug(category.getSlug());
		dto.setImageUrl(category.getImageUrl());
		dto.setIsActive(category.getIsActive());
		dto.setSortOrder(category.getSortOrder());

		if (category.getParent() != null) {
			dto.setParentId(category.getParent().getId());
		}

		return dto;
	}

	// ✅ FIX: renamed from mapToDto → mapToTreeDto to avoid ambiguity
	private CategoryTreeDto mapToTreeDto(Category category) {
		CategoryTreeDto dto = new CategoryTreeDto();
		dto.setId(category.getId());
		dto.setName(category.getName());
		dto.setSlug(category.getSlug());
		dto.setChildren(new ArrayList<>());
		return dto;
	}

	private boolean isCircular(Category newParent, Category current) {
		Category temp = newParent;
		while (temp != null) {
			if (temp.getId().equals(current.getId())) {
				return true;
			}
			temp = temp.getParent();
		}
		return false;
	}
}