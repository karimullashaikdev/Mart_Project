package com.karim.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.StockTransaction;
import com.karim.enums.StockTransactionType;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

	// ✅ create(data) → handled by save()

	// ✅ findByProduct(productId, filters) with pagination
	@Query("""
			    SELECT st FROM StockTransaction st
			    WHERE st.productId = :productId
			""")
	Page<StockTransaction> findByProduct(@Param("productId") UUID productId, Pageable pageable);

	// ✅ findByOrder(orderId)
	List<StockTransaction> findByOrderId(UUID orderId);

	// ✅ findByReturn(returnRequestId)
	List<StockTransaction> findByReturnRequestId(UUID returnRequestId);

	@Query("""
		    SELECT st FROM StockTransaction st
		    WHERE (:productId IS NULL OR st.productId = :productId)
		    AND (:type IS NULL OR st.type = :type)
		    AND (:orderId IS NULL OR st.orderId = :orderId)
		    AND (:returnRequestId IS NULL OR st.returnRequestId = :returnRequestId)
		    AND (:fromDate IS NULL OR st.createdAt >= :fromDate)
		    AND (:toDate IS NULL OR st.createdAt <= :toDate)
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
