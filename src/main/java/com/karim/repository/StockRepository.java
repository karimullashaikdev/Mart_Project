package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    // ✅ findByProductId
    Optional<Stock> findByProductId(UUID productId);

    // ✅ findLowOrOutOfStock
    @Query("""
        SELECT s FROM Stock s
        WHERE s.availableQuantity <= 0
        OR s.availableQuantity <= s.lowStockThreshold
    """)
    List<Stock> findLowOrOutOfStock();

    // ✅ incrementAvailable
    @Modifying
    @Query("""
        UPDATE Stock s
        SET s.availableQuantity = s.availableQuantity + :qty
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
        SET s.reservedQuantity = s.reservedQuantity - :qty
        WHERE s.product.id = :productId
    """)
    void decrementReserved(@Param("productId") UUID productId, @Param("qty") int qty);
}