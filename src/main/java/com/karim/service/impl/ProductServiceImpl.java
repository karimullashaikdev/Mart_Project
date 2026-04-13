package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.BulkPriceUpdateDto;
import com.karim.dto.CreateProductDto;
import com.karim.dto.ProductFilterDto;
import com.karim.dto.ProductResponseDto;
import com.karim.dto.UpdateProductDto;
import com.karim.entity.Category;
import com.karim.entity.Product;
import com.karim.entity.Stock;
import com.karim.repository.CategoryRepository;
import com.karim.repository.ProductRepository;
import com.karim.repository.StockRepository;
import com.karim.service.ProductService;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final StockRepository stockRepository;

	public ProductServiceImpl(ProductRepository productRepository,
			CategoryRepository categoryRepository,
			StockRepository stockRepository) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.stockRepository = stockRepository;
	}

	@Override
	public void createProduct(CreateProductDto dto, UUID actorId) {

		productRepository.findBySku(dto.getSku()).ifPresent(p -> {
			throw new RuntimeException("SKU already exists");
		});

		Category category = categoryRepository.findById(dto.getCategoryId())
				.orElseThrow(() -> new RuntimeException("Category not found"));

		if (Boolean.TRUE.equals(category.getIsDeleted())) {
			throw new RuntimeException("Cannot assign product to deleted category");
		}

		Product product = new Product();
		product.setName(dto.getName());
		product.setSlug(generateSlug(dto.getName()));
		product.setDescription(dto.getDescription());
		product.setSku(dto.getSku());
		product.setBarcode(dto.getBarcode());
		product.setMrp(dto.getMrp());
		product.setSellingPrice(dto.getSellingPrice());
		product.setTaxPercent(dto.getTaxPercent());
		product.setUnit(dto.getUnit());
		product.setUnitValue(dto.getUnitValue());
		product.setImages(dto.getImages());
		product.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
		product.setCategory(category);
		product.setCreatedBy(actorId);

		Product savedProduct = productRepository.save(product);
		createStockRow(savedProduct, actorId);
	}

	@Override
	@Transactional(readOnly = true)
	public ProductResponseDto getProduct(UUID productId) {

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		Stock stock = stockRepository.findActiveByProductId(productId).orElse(null);

		return mapToProductResponse(product, stock);
	}

	@Override
	@Transactional(readOnly = true)
	public ProductResponseDto getProductBySlug(String slug) {

		Product product = productRepository.findBySlug(slug)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		Stock stock = stockRepository.findActiveByProductId(product.getId()).orElse(null);

		return mapToProductResponse(product, stock);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ProductResponseDto> listProducts(ProductFilterDto filters, Pageable pageable) {

		Page<Product> products = productRepository.findProducts(
				filters.getCategoryId(),
				filters.getIsActive(),
				filters.getMinPrice(),
				filters.getMaxPrice(),
				filters.getSearch(),
				pageable
		);

		return products.map(product -> {
			Stock stock = stockRepository.findActiveByProductId(product.getId()).orElse(null);
			return mapToProductResponse(product, stock);
		});
	}

	@Override
	@Transactional
	public void updateProduct(UUID productId, UpdateProductDto dto, UUID actorId) {

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		if (dto.getCategoryId() != null) {
			Category category = categoryRepository.findById(dto.getCategoryId())
					.orElseThrow(() -> new RuntimeException("Category not found"));

			if (Boolean.TRUE.equals(category.getIsDeleted())) {
				throw new RuntimeException("Cannot assign product to deleted category");
			}

			product.setCategory(category);
		}

		if (dto.getName() != null) {
			product.setName(dto.getName());
			product.setSlug(generateSlug(dto.getName()));
		}

		if (dto.getDescription() != null) {
			product.setDescription(dto.getDescription());
		}

		if (dto.getSku() != null) {
			productRepository.findBySku(dto.getSku()).ifPresent(existing -> {
				if (!existing.getId().equals(productId)) {
					throw new RuntimeException("SKU already exists");
				}
			});
			product.setSku(dto.getSku());
		}

		if (dto.getBarcode() != null) {
			product.setBarcode(dto.getBarcode());
		}

		if (dto.getMrp() != null) {
			product.setMrp(dto.getMrp());
		}

		if (dto.getSellingPrice() != null) {
			product.setSellingPrice(dto.getSellingPrice());
		}

		if (dto.getTaxPercent() != null) {
			product.setTaxPercent(dto.getTaxPercent());
		}

		if (dto.getUnit() != null) {
			product.setUnit(dto.getUnit());
		}

		if (dto.getUnitValue() != null) {
			product.setUnitValue(dto.getUnitValue());
		}

		if (dto.getImages() != null) {
			product.setImages(dto.getImages());
		}

		if (dto.getIsActive() != null) {
			product.setIsActive(dto.getIsActive());
		}

		product.setUpdatedBy(actorId);
		productRepository.save(product);
	}

	@Override
	@Transactional
	public void toggleProductActive(UUID productId, UUID actorId) {

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		Boolean currentStatus = product.getIsActive() != null ? product.getIsActive() : true;
		product.setIsActive(!currentStatus);
		product.setUpdatedBy(actorId);

		productRepository.save(product);
	}

	@Override
	@Transactional
	public void softDeleteProduct(UUID productId, UUID actorId) {

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		product.setIsDeleted(true);
		product.setDeletedAt(LocalDateTime.now());
		product.setDeletedBy(actorId);
		product.setUpdatedBy(actorId);

		productRepository.save(product);

		Stock stock = stockRepository.findActiveByProductId(productId).orElse(null);

		if (stock != null) {
			stock.setIsDeleted(true);
			stock.setDeletedAt(LocalDateTime.now());
			stock.setDeletedBy(actorId);
			stock.setUpdatedBy(actorId);
			stockRepository.save(stock);
		}
	}

	@Override
	@Transactional
	public void restoreProduct(UUID productId, UUID actorId) {

		Product product = productRepository.findByIdIncludingDeleted(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		product.setIsDeleted(false);
		product.setDeletedAt(null);
		product.setDeletedBy(null);
		product.setUpdatedBy(actorId);
		product.setUpdatedAt(LocalDateTime.now());

		productRepository.save(product);
	}

	@Override
	@Transactional
	public void bulkUpdatePrices(List<BulkPriceUpdateDto> items, UUID actorId) {

		for (BulkPriceUpdateDto item : items) {
			Product product = productRepository.findById(item.getProductId())
					.orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

			if (item.getMrp() != null) {
				product.setMrp(item.getMrp().doubleValue());
			}

			if (item.getSellingPrice() != null) {
				product.setSellingPrice(item.getSellingPrice().doubleValue());
			}

			product.setUpdatedBy(actorId);
			productRepository.save(product);
		}
	}

	// ---------------- Helper Methods ----------------

	private void createStockRow(Product product, UUID actorId) {
		stockRepository.findActiveByProductId(product.getId()).ifPresent(existing -> {
			throw new RuntimeException("Stock already exists for product: " + product.getId());
		});

		Stock stock = new Stock();
		stock.setProduct(product);
		stock.setQuantityAvailable(0);
		stock.setQuantityReserved(0);
		stock.setReorderLevel(5);
		stock.setReorderQuantity(10);
		stock.setUpdatedBy(actorId);

		stockRepository.save(stock);
	}

	private ProductResponseDto mapToProductResponse(Product product, Stock stock) {
		ProductResponseDto dto = new ProductResponseDto();

		dto.setId(product.getId());
		dto.setName(product.getName());
		dto.setSlug(product.getSlug());
		dto.setDescription(product.getDescription());
		dto.setSku(product.getSku());
		dto.setBarcode(product.getBarcode());

		dto.setMrp(product.getMrp());
		dto.setSellingPrice(product.getSellingPrice());
		dto.setTaxPercent(product.getTaxPercent());

		dto.setUnit(product.getUnit());
		dto.setUnitValue(product.getUnitValue());

		dto.setImages(product.getImages());
		dto.setIsActive(product.getIsActive());

		if (product.getCategory() != null) {
			dto.setCategoryId(product.getCategory().getId());
		}

		if (stock != null) {
			int availableQty = stock.getQuantityAvailable() != null ? stock.getQuantityAvailable() : 0;
			int reservedQty = stock.getQuantityReserved() != null ? stock.getQuantityReserved() : 0;

			dto.setAvailableQuantity(availableQty);
			dto.setReservedQuantity(reservedQty);
			dto.setInStock(availableQty > 0);
		} else {
			dto.setAvailableQuantity(0);
			dto.setReservedQuantity(0);
			dto.setInStock(false);
		}

		return dto;
	}

	private String generateSlug(String name) {
		return name.toLowerCase()
				.trim()
				.replaceAll("[^a-z0-9\\s]", "")
				.replaceAll("\\s+", "-");
	}
}