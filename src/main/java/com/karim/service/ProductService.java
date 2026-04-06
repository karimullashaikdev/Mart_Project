package com.karim.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.karim.dto.BulkPriceUpdateDto;
import com.karim.dto.CreateProductDto;
import com.karim.dto.ProductFilterDto;
import com.karim.dto.ProductResponseDto;
import com.karim.dto.UpdateProductDto;

public interface ProductService {

	void createProduct(CreateProductDto dto, UUID actorId);

	ProductResponseDto getProduct(UUID productId);

	ProductResponseDto getProductBySlug(String slug);

	Page<ProductResponseDto> listProducts(ProductFilterDto filters, Pageable pageable);
	
	void updateProduct(UUID productId, UpdateProductDto dto, UUID actorId);
	
	 void toggleProductActive(UUID productId, UUID actorId);
	 
	 void softDeleteProduct(UUID productId, UUID actorId);
	 
	 void restoreProduct(UUID productId, UUID actorId);
	 
	 void bulkUpdatePrices(List<BulkPriceUpdateDto> items, UUID actorId);
}
