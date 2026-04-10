package com.karim.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karim.dto.BulkPriceUpdateDto;
import com.karim.dto.CreateProductDto;
import com.karim.dto.ProductFilterDto;
import com.karim.dto.ProductResponseDto;
import com.karim.dto.UpdateProductDto;
import com.karim.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Product Management", description = "APIs for managing products, pricing, stock visibility, activation, soft delete, and restore operations")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	@Operation(summary = "Create a new product", description = "Creates a new product for the given category. "
			+ "This operation validates SKU uniqueness, validates the category, "
			+ "generates product slug from the product name, saves the product, " + "and creates an initial stock row.")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Product created successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request / SKU already exists / invalid category", content = @Content),
			@ApiResponse(responseCode = "404", description = "Category not found", content = @Content) })
	public ResponseEntity<String> createProduct(@Valid @RequestBody CreateProductDto dto,
			@Parameter(description = "User ID performing the action", required = true, example = "d290f1ee-6c54-4b01-90e6-d701748f0851") @RequestHeader("X-Actor-Id") UUID actorId) {

		productService.createProduct(dto, actorId);
		return ResponseEntity.ok("Product created successfully");
	}

	@GetMapping("/{productId}")
	@Operation(summary = "Get product by ID", description = "Fetches product details by product ID, including stock details such as available quantity, reserved quantity, and stock status.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Product fetched successfully", content = @Content(schema = @Schema(implementation = ProductResponseDto.class))),
			@ApiResponse(responseCode = "404", description = "Product not found", content = @Content) })
	public ResponseEntity<ProductResponseDto> getProduct(
			@Parameter(description = "Unique product ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID productId) {

		return ResponseEntity.ok(productService.getProduct(productId));
	}

	@GetMapping("/slug/{slug}")
	@Operation(summary = "Get product by slug", description = "Fetches product details using the product slug. "
			+ "Also includes stock information like available quantity, reserved quantity, and in-stock flag.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Product fetched successfully by slug", content = @Content(schema = @Schema(implementation = ProductResponseDto.class))),
			@ApiResponse(responseCode = "404", description = "Product not found", content = @Content) })
	public ResponseEntity<ProductResponseDto> getProductBySlug(
			@Parameter(description = "Product slug", required = true, example = "amul-taaza-toned-milk-1l") @PathVariable String slug) {

		return ResponseEntity.ok(productService.getProductBySlug(slug));
	}

	@GetMapping
	@Operation(summary = "List all products with filters", description = "Returns paginated products using optional filters such as category, active status, minimum price, maximum price, and search text.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Products fetched successfully", content = @Content(schema = @Schema(implementation = ProductResponseDto.class))) })
	@Parameters(value = { @Parameter(name = "categoryId", description = "Filter by category ID"),
			@Parameter(name = "isActive", description = "Filter by active/inactive status"),
			@Parameter(name = "minPrice", description = "Filter by minimum selling price"),
			@Parameter(name = "maxPrice", description = "Filter by maximum selling price"),
			@Parameter(name = "search", description = "Search by product name, SKU, or related fields"),
			@Parameter(name = "page", description = "Page number, starting from 0"),
			@Parameter(name = "size", description = "Page size"),
			@Parameter(name = "sort", description = "Sorting criteria. Example: name,asc") })
	public ResponseEntity<Page<ProductResponseDto>> listProducts(@ModelAttribute ProductFilterDto filters,
			@PageableDefault(size = 10) Pageable pageable) {

		return ResponseEntity.ok(productService.listProducts(filters, pageable));
	}

	@PutMapping("/{productId}")
	@Operation(summary = "Update product", description = "Updates product details such as category, name, description, SKU, barcode, price fields, unit details, images, and active status. "
			+ "If the name is changed, slug is regenerated.")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Product updated successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request / SKU already exists", content = @Content),
			@ApiResponse(responseCode = "404", description = "Product or category not found", content = @Content) })
	public ResponseEntity<String> updateProduct(
			@Parameter(description = "Unique product ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID productId,
			@Valid @RequestBody UpdateProductDto dto,
			@Parameter(description = "User ID performing the action", required = true, example = "d290f1ee-6c54-4b01-90e6-d701748f0851") @RequestHeader("X-Actor-Id") UUID actorId) {

		productService.updateProduct(productId, dto, actorId);
		return ResponseEntity.ok("Product updated successfully");
	}

	@PatchMapping("/{productId}/toggle-active")
	@Operation(summary = "Toggle product active status", description = "Toggles the product active status. If currently active, it becomes inactive. If inactive, it becomes active.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Product active status toggled successfully"),
			@ApiResponse(responseCode = "404", description = "Product not found", content = @Content) })
	public ResponseEntity<String> toggleProductActive(
			@Parameter(description = "Unique product ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID productId,
			@Parameter(description = "User ID performing the action", required = true, example = "d290f1ee-6c54-4b01-90e6-d701748f0851") @RequestHeader("X-Actor-Id") UUID actorId) {

		productService.toggleProductActive(productId, actorId);
		return ResponseEntity.ok("Product active status toggled successfully");
	}

	@DeleteMapping("/{productId}")
	@Operation(summary = "Soft delete product", description = "Soft deletes the product by marking it as deleted. "
			+ "Associated stock is also soft deleted if available.")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Product soft deleted successfully"),
			@ApiResponse(responseCode = "404", description = "Product not found", content = @Content) })
	public ResponseEntity<String> softDeleteProduct(
			@Parameter(description = "Unique product ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID productId,
			@Parameter(description = "User ID performing the action", required = true, example = "d290f1ee-6c54-4b01-90e6-d701748f0851") @RequestHeader("X-Actor-Id") UUID actorId) {

		productService.softDeleteProduct(productId, actorId);
		return ResponseEntity.ok("Product soft deleted successfully");
	}

	@PatchMapping("/{productId}/restore")
	@Operation(summary = "Restore soft deleted product", description = "Restores a soft deleted product by clearing deleted flags and audit fields.")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Product restored successfully"),
			@ApiResponse(responseCode = "404", description = "Product not found", content = @Content) })
	public ResponseEntity<String> restoreProduct(
			@Parameter(description = "Unique product ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID productId,
			@Parameter(description = "User ID performing the action", required = true, example = "d290f1ee-6c54-4b01-90e6-d701748f0851") @RequestHeader("X-Actor-Id") UUID actorId) {

		productService.restoreProduct(productId, actorId);
		return ResponseEntity.ok("Product restored successfully");
	}

	@PutMapping("/bulk-price-update")
	@Operation(summary = "Bulk update product prices", description = "Updates MRP and/or selling price for multiple products in a single request.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Bulk price update completed successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
			@ApiResponse(responseCode = "404", description = "One or more products not found", content = @Content) })
	public ResponseEntity<String> bulkUpdatePrices(
			@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of products with updated MRP and/or selling price", required = true, content = @Content(array = @ArraySchema(schema = @Schema(implementation = BulkPriceUpdateDto.class)))) @RequestBody List<BulkPriceUpdateDto> items,
			@Parameter(description = "User ID performing the action", required = true, example = "d290f1ee-6c54-4b01-90e6-d701748f0851") @RequestHeader("X-Actor-Id") UUID actorId) {

		productService.bulkUpdatePrices(items, actorId);
		return ResponseEntity.ok("Bulk price update completed successfully");
	}
}