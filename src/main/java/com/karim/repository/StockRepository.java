package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Stock;

import jakarta.transaction.Transactional;

public interface StockRepository extends JpaRepository<Stock, UUID> {

	// ✅ Explicit active stock fetch by product id
	@Query("""
			    SELECT s
			    FROM Stock s
			    JOIN FETCH s.product p
			    WHERE p.id = :productId
			      AND s.isDeleted = false
			""")
	Optional<Stock> findActiveByProductId(@Param("productId") UUID productId);

	// ✅ Low or out of stock (only active)
	@Query("""
			    SELECT s
			    FROM Stock s
			    JOIN FETCH s.product p
			    WHERE s.isDeleted = false
			      AND s.status IN ('LOW_STOCK', 'OUT_OF_STOCK')
			""")
	List<Stock> findLowStockProducts();

	// ✅ Manual increase available stock + status + updated time
	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			       SET s.quantityAvailable = s.quantityAvailable + :qty,
			           s.lastUpdatedAt = :now,
			           s.updatedBy = :actorId,
			           s.status = CASE
			               WHEN (s.quantityAvailable + :qty) <= 0 THEN com.karim.enums.StockStatus.OUT_OF_STOCK
			               WHEN s.reorderLevel IS NOT NULL AND (s.quantityAvailable + :qty) <= s.reorderLevel THEN com.karim.enums.StockStatus.LOW_STOCK
			               ELSE com.karim.enums.StockStatus.IN_STOCK
			           END
			     WHERE s.product.id = :productId
			       AND s.isDeleted = false
			""")
	int incrementAvailable(@Param("productId") UUID productId, @Param("qty") int qty, @Param("actorId") UUID actorId,
			@Param("now") LocalDateTime now);

	// ✅ Manual decrease available stock + status + updated time
	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			       SET s.quantityAvailable = s.quantityAvailable - :qty,
			           s.lastUpdatedAt = :now,
			           s.updatedBy = :actorId,
			           s.status = CASE
			               WHEN (s.quantityAvailable - :qty) <= 0 THEN com.karim.enums.StockStatus.OUT_OF_STOCK
			               WHEN s.reorderLevel IS NOT NULL AND (s.quantityAvailable - :qty) <= s.reorderLevel THEN com.karim.enums.StockStatus.LOW_STOCK
			               ELSE com.karim.enums.StockStatus.IN_STOCK
			           END
			     WHERE s.product.id = :productId
			       AND s.isDeleted = false
			""")
	int decrementAvailable(@Param("productId") UUID productId, @Param("qty") int qty, @Param("actorId") UUID actorId,
			@Param("now") LocalDateTime now);

	// ✅ Increase reserved
	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			       SET s.quantityReserved = s.quantityReserved + :qty,
			           s.lastUpdatedAt = :now,
			           s.updatedBy = :actorId
			     WHERE s.product.id = :productId
			       AND s.isDeleted = false
			""")
	int incrementReserved(@Param("productId") UUID productId, @Param("qty") int qty, @Param("actorId") UUID actorId,
			@Param("now") LocalDateTime now);

	// ✅ Decrease reserved
	@Modifying
	@Transactional
	@Query("""
			    UPDATE Stock s
			       SET s.quantityReserved = s.quantityReserved - :qty,
			           s.lastUpdatedAt = :now,
			           s.updatedBy = :actorId
			     WHERE s.product.id = :productId
			       AND s.isDeleted = false
			""")
	int decrementReserved(@Param("productId") UUID productId, @Param("qty") int qty, @Param("actorId") UUID actorId,
			@Param("now") LocalDateTime now);
}