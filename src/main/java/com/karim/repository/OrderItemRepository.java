package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // ✅ findByOrder(orderId)
	List<OrderItem> findByOrder_Id(UUID orderId);

    // ✅ findById
    Optional<OrderItem> findById(UUID id);

    // ✅ bulkCreate(items) → handled via saveAll()

    // ✅ updateItemStatus
    @Modifying
    @Query("""
        UPDATE OrderItem oi
        SET oi.status = :status
        WHERE oi.id = :id
    """)
    void updateItemStatus(@Param("id") UUID id, @Param("status") String status);
}
