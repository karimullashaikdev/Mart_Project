package com.karim.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karim.anootation.CurrentUser;
import com.karim.dto.ApiResponse;
import com.karim.dto.CategoryFilterDto;
import com.karim.dto.CategoryResponseDto;
import com.karim.dto.CategoryTreeDto;
import com.karim.dto.CreateCategoryDto;
import com.karim.dto.ReorderCategoriesDto;
import com.karim.dto.UpdateCategoryDto;
import com.karim.service.CategoryService;
import com.karim.service.impl.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Category", description = "Category management APIs — public reads, admin writes")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PUBLIC — read-only
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping
    @SecurityRequirements
    @Operation(
            summary = "Get category tree (Public)",
            description = "Returns the full nested category tree containing only active, non-deleted categories. "
                    + "Each root category includes its children recursively. "
                    + "No authentication required.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Category tree returned successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryTreeDto.class)))) })
    public ResponseEntity<ApiResponse<List<CategoryTreeDto>>> getCategoryTree() {

        List<CategoryTreeDto> tree = categoryService.getCategoryTree();
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @GetMapping("/{categoryIdOrSlug}")
    @SecurityRequirements
    @Operation(
            summary = "Get a single category (Public)",
            description = "Fetches a single category by its UUID or slug. "
                    + "Pass either a valid UUID (e.g. `3fa85f64-5717-4562-b3fc-2c963f66afa6`) "
                    + "or a URL-friendly slug (e.g. `mens-clothing`). "
                    + "No authentication required.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Category found",
                    content = @Content(schema = @Schema(implementation = CategoryResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Category not found with the given ID or slug") })
    public ResponseEntity<ApiResponse<CategoryResponseDto>> getCategory(
            @Parameter(description = "Category UUID or slug", required = true, example = "mens-clothing")
            @PathVariable String categoryIdOrSlug) {

        CategoryResponseDto category = categoryService.getCategory(categoryIdOrSlug);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADMIN — write operations
    // ─────────────────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create a category (Admin)",
            description = "Creates a new category. "
                    + "Optionally assign a `parentId` to make it a subcategory. "
                    + "If `sortOrder` is omitted it is auto-assigned as `total_count + 1`. "
                    + "A URL-friendly slug is auto-generated from the name. "
                    + "Requires ADMIN role.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Category created successfully",
                    content = @Content(schema = @Schema(implementation = CategoryResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error in request body"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorised – JWT missing or invalid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden – admin role required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Parent category not found (if parentId is provided)") })
    public ResponseEntity<ApiResponse<CategoryResponseDto>> createCategory(
            @Valid @RequestBody CreateCategoryDto dto,
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

        CategoryResponseDto created = categoryService.createCategory(dto, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PatchMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update a category (Admin)",
            description = "Partially updates a category's fields. Only non-null fields are applied. "
                    + "If `name` is updated, the slug is regenerated automatically. "
                    + "If `parentId` is provided, circular dependency and self-reference checks are enforced. "
                    + "Requires ADMIN role.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Category updated successfully",
                    content = @Content(schema = @Schema(implementation = CategoryResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error or circular parent dependency detected"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorised"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden – admin role required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Category or parent category not found") })
    public ResponseEntity<ApiResponse<CategoryResponseDto>> updateCategory(
            @Parameter(description = "UUID of the category to update", required = true)
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryDto dto,
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

        CategoryResponseDto updated = categoryService.updateCategory(categoryId, dto, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PatchMapping("/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Reorder categories (Admin)",
            description = "Accepts an ordered list of category UUIDs and reassigns `sortOrder` "
                    + "starting from 1 in the given sequence. "
                    + "All provided IDs must exist — a 404 is returned if any ID is not found. "
                    + "Requires ADMIN role.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Categories reordered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Ordered IDs list is null or empty"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorised"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden – admin role required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "One or more category IDs not found") })
    public ResponseEntity<ApiResponse<String>> reorderCategories(
            @Valid @RequestBody ReorderCategoriesDto dto,
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

        categoryService.reorderCategories(dto.getOrderedIds(), principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Categories reordered successfully"));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Soft-delete a category (Admin)",
            description = "Marks a category as deleted (sets `is_deleted=true`, `deleted_at`, `deleted_by`). "
                    + "The category will no longer appear in public category tree queries. "
                    + "Deletion is blocked if the category has active products linked to it, "
                    + "or if it has active non-deleted child categories. "
                    + "Requires ADMIN role.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Category soft-deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Cannot delete — category has active products or active child categories"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorised"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden – admin role required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Category not found") })
    public ResponseEntity<Void> softDeleteCategory(
            @Parameter(description = "UUID of the category to delete", required = true)
            @PathVariable UUID categoryId,
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

        categoryService.softDeleteCategory(categoryId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{categoryId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Restore a soft-deleted category (Admin)",
            description = "Reverses a soft-delete by setting `is_deleted=false` and clearing "
                    + "`deleted_at` and `deleted_by`. "
                    + "Returns 400 if the category is not currently deleted. "
                    + "Returns 400 if the category's parent is still deleted — restore the parent first. "
                    + "Requires ADMIN role.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Category restored successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Category is not deleted, or its parent category is still deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorised"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden – admin role required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Category not found") })
    public ResponseEntity<ApiResponse<String>> restoreCategory(
            @Parameter(description = "UUID of the soft-deleted category to restore", required = true)
            @PathVariable UUID categoryId,
            @Parameter(hidden = true) @CurrentUser UserPrincipal principal) {

        categoryService.restoreCategory(categoryId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Category restored successfully"));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List all categories with filters (Admin)",
            description = "Returns a flat list of ALL categories (including soft-deleted ones) "
                    + "with optional filtering. All filter params are optional — omit them to return everything. "
                    + "Requires ADMIN role.\n\n"
                    + "**Filters:**\n"
                    + "- `name` — case-insensitive partial match on category name\n"
                    + "- `isActive` — `true` / `false`\n"
                    + "- `isDeleted` — `true` to see deleted categories, `false` for active only\n"
                    + "- `parentId` — filter by parent category UUID (returns direct children only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "List of categories returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryResponseDto.class)))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorised"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden – admin role required") })
    public ResponseEntity<ApiResponse<List<CategoryResponseDto>>> listCategoriesAdmin(
            @Valid CategoryFilterDto filters) {

        List<CategoryResponseDto> categories = categoryService.listCategoriesAdmin(filters);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }
}
