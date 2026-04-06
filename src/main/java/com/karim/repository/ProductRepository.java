package com.karim.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // ✅ findBySlug
    Optional<Product> findBySlug(String slug);

    // ✅ findBySku
    Optional<Product> findBySku(String sku);

    // ✅ list with filters + pagination (basic version)
    @Query("""
    	    SELECT p FROM Product p
    	    WHERE (:categoryId IS NULL OR p.category.id = :categoryId)
    	    AND (:isActive IS NULL OR p.isActive = :isActive)
    	    AND (:minPrice IS NULL OR p.sellingPrice >= :minPrice)
    	    AND (:maxPrice IS NULL OR p.sellingPrice <= :maxPrice)
    	""")
    	List<Product> list(
    	        @Param("categoryId") UUID categoryId,
    	        @Param("isActive") Boolean isActive,
    	        @Param("minPrice") Double minPrice,
    	        @Param("maxPrice") Double maxPrice
    	);

    // (Pagination handled in service layer using Pageable if needed)

    // ✅ bulkUpdatePrices
    @Modifying
    @Query("""
        UPDATE Product p
        SET p.price = :price
        WHERE p.id = :id
    """)
    void updatePrice(@Param("id") UUID id, @Param("price") BigDecimal price);

    // (Loop in service for bulk updates)
    
    @Query("""
            SELECT p FROM Product p
            WHERE (:categoryId IS NULL OR p.category.id = :categoryId)
            AND (:isActive IS NULL OR p.isActive = :isActive)
            AND (:minPrice IS NULL OR p.sellingPrice >= :minPrice)
            AND (:maxPrice IS NULL OR p.sellingPrice <= :maxPrice)
            AND (
                :search IS NULL OR
                LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
            )
        """)
        Page<Product> findProducts(
                @Param("categoryId") UUID categoryId,
                @Param("isActive") Boolean isActive,
                @Param("minPrice") BigDecimal minPrice,
                @Param("maxPrice") BigDecimal maxPrice,
                @Param("search") String search,
                Pageable pageable
        );
    
    @Query(value = "SELECT * FROM products WHERE id = :id", nativeQuery = true)
    Optional<Product> findByIdIncludingDeleted(@Param("id") UUID id);

}
