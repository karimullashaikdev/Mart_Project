package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.OrderItem;
import com.karim.enums.OrderItemStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // ✅ findByOrder(orderId)
	List<OrderItem> findByOrder_Id(UUID orderId);

    Optional<OrderItem> findById(UUID id);

    @Modifying
    @Transactional
    @Query("""
        UPDATE OrderItem oi
        SET oi.itemStatus = :status
        WHERE oi.id = :id
    """)
    void updateItemStatus(@Param("id") UUID id,
                          @Param("status") OrderItemStatus status);
}
