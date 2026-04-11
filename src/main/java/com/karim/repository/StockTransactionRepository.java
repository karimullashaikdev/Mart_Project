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

    // Find all transactions for a product with pagination
    @Query("""
            SELECT st
            FROM StockTransaction st
            WHERE st.productId = :productId
            ORDER BY st.createdAt DESC
           """)
    Page<StockTransaction> findByProductId(@Param("productId") UUID productId, Pageable pageable);

    // Find transactions by orderId
    List<StockTransaction> findByOrderId(UUID orderId);

    // Find transactions by returnRequestId
    List<StockTransaction> findByReturnRequestId(UUID returnRequestId);

    // Flexible filter method
    @Query("""
            SELECT st
            FROM StockTransaction st
            WHERE (:productId IS NULL OR st.productId = :productId)
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