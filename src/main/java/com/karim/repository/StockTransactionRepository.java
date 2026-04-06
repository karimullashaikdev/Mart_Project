package com.karim.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.StockTransaction;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

    // ✅ create(data) → handled by save()

    // ✅ findByProduct(productId, filters) with pagination
    @Query("""
        SELECT st FROM StockTransaction st
        WHERE st.productId = :productId
    """)
    Page<StockTransaction> findByProduct(
            @Param("productId") UUID productId,
            Pageable pageable
    );

    // ✅ findByOrder(orderId)
    List<StockTransaction> findByOrderId(UUID orderId);

    // ✅ findByReturn(returnRequestId)
    List<StockTransaction> findByReturnRequestId(UUID returnRequestId);
}
