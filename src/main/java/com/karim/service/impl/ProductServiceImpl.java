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

	public ProductServiceImpl(ProductRepository productRepository, CategoryRepository categoryRepository,
			StockRepository stockRepository) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.stockRepository = stockRepository;
	}

	@Override
	public void createProduct(CreateProductDto dto, UUID actorId) {

		// ✅ Validate SKU uniqueness
		productRepository.findBySku(dto.getSku()).ifPresent(p -> {
			throw new RuntimeException("SKU already exists");
		});

		// ✅ Fetch category
		Category category = categoryRepository.findById(dto.getCategoryId())
				.orElseThrow(() -> new RuntimeException("Category not found"));

		// ❌ Prevent assigning to deleted/inactive category
		if (Boolean.TRUE.equals(category.getIsDeleted())) {
			throw new RuntimeException("Cannot assign product to deleted category");
		}

		// ✅ Create product
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

		// ✅ Audit
		product.setCreatedBy(actorId);

		// Save product
		Product savedProduct = productRepository.save(product);

		// ✅ Create STOCK row (qty = 0)
		createStockRow(savedProduct, actorId);
	}

	@Override
	public ProductResponseDto getProduct(UUID productId) {

		// ✅ Fetch product
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		// ✅ Fetch stock
		Stock stock = stockRepository.findByProductId(productId).orElse(null);

		// ✅ Map response
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

		// ✅ Stock mapping
		if (stock != null) {
			dto.setAvailableQuantity(stock.getQuantityAvailable());
			dto.setReservedQuantity(stock.getQuantityReserved());
			dto.setInStock(stock.getQuantityAvailable() != null && stock.getQuantityAvailable() > 0);
		} else {
			dto.setAvailableQuantity(0);
			dto.setReservedQuantity(0);
			dto.setInStock(false);
		}

		return dto;
	}

	@Override
	public ProductResponseDto getProductBySlug(String slug) {

		// ✅ Fetch product by slug
		Product product = productRepository.findBySlug(slug)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		// ✅ Fetch stock
		Stock stock = stockRepository.findByProductId(product.getId()).orElse(null);

		// ✅ Map to DTO
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

		// ✅ Stock mapping
		if (stock != null) {
			dto.setAvailableQuantity(stock.getQuantityAvailable());
			dto.setReservedQuantity(stock.getQuantityReserved());
			dto.setInStock(stock.getQuantityAvailable() != null && stock.getQuantityAvailable() > 0);
		} else {
			dto.setAvailableQuantity(0);
			dto.setReservedQuantity(0);
			dto.setInStock(false);
		}

		return dto;
	}

	@Override
	public Page<ProductResponseDto> listProducts(ProductFilterDto filters, Pageable pageable) {

		Page<Product> products = productRepository.findProducts(filters.getCategoryId(), filters.getIsActive(),
				filters.getMinPrice(), filters.getMaxPrice(), filters.getSearch(), pageable);

		// Map to DTO including stock
		return products.map(product -> {

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

			// Stock
			Stock stock = stockRepository.findByProductId(product.getId()).orElse(null);

			if (stock != null) {
				dto.setAvailableQuantity(stock.getQuantityAvailable());
				dto.setReservedQuantity(stock.getQuantityReserved());
				dto.setInStock(stock.getQuantityAvailable() != null && stock.getQuantityAvailable() > 0);
			} else {
				dto.setAvailableQuantity(0);
				dto.setReservedQuantity(0);
				dto.setInStock(false);
			}

			return dto;
		});
	}

	@Override
	@Transactional
	public void updateProduct(UUID productId, UpdateProductDto dto, UUID actorId) {

		// ✅ Fetch product
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		// ✅ Update category if provided
		if (dto.getCategoryId() != null) {
			Category category = categoryRepository.findById(dto.getCategoryId())
					.orElseThrow(() -> new RuntimeException("Category not found"));
			product.setCategory(category);
		}

		// ✅ Update name + slug if name changed
		if (dto.getName() != null) {
			product.setName(dto.getName());
			product.setSlug(generateSlug(dto.getName()));
		}

		// ✅ Other fields
		if (dto.getDescription() != null) {
			product.setDescription(dto.getDescription());
		}

		if (dto.getSku() != null) {
			// Optional: check SKU uniqueness
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

		// ✅ Audit fields
		product.setUpdatedBy(actorId);

		// ✅ Save
		productRepository.save(product);
	}

	@Override
	@Transactional
	public void toggleProductActive(UUID productId, UUID actorId) {

		// ✅ Fetch product
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		// ✅ Toggle isActive
		Boolean currentStatus = product.getIsActive() != null ? product.getIsActive() : true;
		product.setIsActive(!currentStatus);

		// ✅ Audit
		product.setUpdatedBy(actorId);

		// ✅ Save
		productRepository.save(product);
	}

	@Override
	@Transactional
	public void softDeleteProduct(UUID productId, UUID actorId) {

		// ✅ Fetch product
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		// ✅ Soft delete product
		product.setIsDeleted(true);
		product.setDeletedAt(LocalDateTime.now());
		product.setDeletedBy(actorId);

		productRepository.save(product);

		// ✅ Fetch stock
		Stock stock = stockRepository.findByProductId(productId).orElse(null);

		if (stock != null) {
			stock.setIsDeleted(true);
			stock.setDeletedAt(LocalDateTime.now());
			stock.setDeletedBy(actorId);

			stockRepository.save(stock);
		}
	}
	
	@Override
    @Transactional
    public void restoreProduct(UUID productId, UUID actorId) {

        // ⚠️ IMPORTANT: Because of @SQLRestriction(is_deleted = false),
        // soft-deleted records are NOT returned by default.
        // So we must use a custom query to fetch them.

        Product product = productRepository.findByIdIncludingDeleted(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // ✅ Restore product
        product.setIsDeleted(false);
        product.setDeletedAt(null);
        product.setDeletedBy(null);

        // Optional: track who restored it
        product.setUpdatedBy(actorId);
        product.setUpdatedAt(LocalDateTime.now());

        productRepository.save(product);
    }
	
	@Override
    @Transactional
    public void bulkUpdatePrices(List<BulkPriceUpdateDto> items, UUID actorId) {

        for (BulkPriceUpdateDto item : items) {

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found: " + item.getProductId()
                    ));

            // ✅ Update MRP if provided
            if (item.getMrp() != null) {
                product.setMrp(item.getMrp().doubleValue());
            }

            // ✅ Update Selling Price if provided
            if (item.getSellingPrice() != null) {
                product.setSellingPrice(item.getSellingPrice().doubleValue());
            }

            // ✅ Audit
            product.setUpdatedBy(actorId);

            productRepository.save(product);
        }
    }

	// ---------------- Helper Methods ----------------

	private void createStockRow(Product product, UUID actorId) {
	    stockRepository.findByProductId(product.getId()).ifPresent(existing -> {
	        throw new RuntimeException("Stock already exists for product: " + product.getId());
	    });

	    Stock stock = new Stock();
	    stock.setProduct(product);
	    stock.setQuantityAvailable(0);
	    stock.setQuantityReserved(0);
	    stock.setReorderLevel(5);      // optional
	    stock.setReorderQuantity(10);  // optional
	    stock.setUpdatedBy(actorId);

	    stockRepository.save(stock);
	}

	private String generateSlug(String name) {
		return name.toLowerCase().trim().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "-");
	}
}
