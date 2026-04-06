package com.karim.repository;

import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	@Query("""
			    UPDATE Stock s
			    SET s.quantityAvailable = s.quantityAvailable + :qty
			    WHERE s.product.id = :productId
			""")
	void incrementAvailable(@Param("productId") UUID productId, @Param("qty") int qty);

	// ✅ decrementAvailable
	@Modifying
	@Query("""
			    UPDATE Stock s
			    SET s.availableQuantity = s.availableQuantity - :qty
			    WHERE s.product.id = :productId
			""")
	void decrementAvailable(@Param("productId") UUID productId, @Param("qty") int qty);

	// ✅ incrementReserved
	@Modifying
	@Query("""
			    UPDATE Stock s
			    SET s.reservedQuantity = s.reservedQuantity + :qty
			    WHERE s.product.id = :productId
			""")
	void incrementReserved(@Param("productId") UUID productId, @Param("qty") int qty);

	// ✅ decrementReserved
	@Modifying
	@Query("""
			    UPDATE Stock s
			    SET s.quantityReserved = s.quantityReserved - :qty
			    WHERE s.product.id = :productId
			""")
	void decrementReserved(@Param("productId") UUID productId, @Param("qty") int qty);
	
	@Query("""
		    SELECT st FROM StockTransaction st
		    WHERE st.productId = :productId
		    AND (:type IS NULL OR st.type = :type)
		    AND (:orderId IS NULL OR st.orderId = :orderId)
		    AND (:returnRequestId IS NULL OR st.returnRequestId = :returnRequestId)
		    AND (:fromDate IS NULL OR st.createdAt >= :fromDate)
		    AND (:toDate IS NULL OR st.createdAt <= :toDate)
		    ORDER BY st.createdAt DESC
		""")
		Page<StockTransaction> findTransactions(
		        @Param("productId") UUID productId,
		        @Param("type") StockTransactionType type,
		        @Param("orderId") UUID orderId,
		        @Param("returnRequestId") UUID returnRequestId,
		        @Param("fromDate") LocalDateTime fromDate,
		        @Param("toDate") LocalDateTime toDate,
		        Pageable pageable
		);
}