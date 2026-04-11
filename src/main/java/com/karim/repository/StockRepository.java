package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.Stock;
import com.karim.entity.StockTransaction;
import com.karim.enums.StockTransactionType;

public interface StockRepository extends JpaRepository<Stock, UUID> {

	// ✅ findByProductId
	Optional<Stock> findByProductId(UUID productId);

	// ✅ findLowOrOutOfStock
	@Query("""
			    SELECT s FROM Stock s
			    JOIN FETCH s.product p
			    WHERE s.status IN ('LOW_STOCK', 'OUT_OF_STOCK')
			""")
	List<Stock> findLowStockProducts();

	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			    SET s.quantityAvailable = s.quantityAvailable + :qty
			    WHERE s.product.id = :productId
			""")
	void incrementAvailable(@Param("productId") UUID productId, @Param("qty") int qty);

	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			    SET s.quantityAvailable = s.quantityAvailable - :qty
			    WHERE s.product.id = :productId
			""")
	void decrementAvailable(@Param("productId") UUID productId, @Param("qty") int qty);

	// ✅ incrementReserved
	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			    SET s.quantityReserved = s.quantityReserved + :qty
			    WHERE s.product.id = :productId
			""")
	void incrementReserved(@Param("productId") UUID productId, @Param("qty") int qty);

	// ✅ decrementReserved
	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			    SET s.quantityReserved = s.quantityReserved - :qty
			    WHERE s.product.id = :productId
			""")
	void decrementReserved(@Param("productId") UUID productId, @Param("qty") int qty);

	
}