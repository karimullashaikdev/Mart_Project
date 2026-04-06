package com.karim.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // ✅ findById
    Optional<Order> findById(UUID id);

    // ✅ findByNumber
    Optional<Order> findByOrderNumber(String orderNumber);

    // ✅ findByUser with pagination
    @Query("""
        SELECT o FROM Order o
        WHERE o.userId = :userId
    """)
    Page<Order> findByUser(@Param("userId") UUID userId, Pageable pageable);

    // ✅ listAdmin (no user filter, includes all except soft deleted handled by entity)
    @Query("""
        SELECT o FROM Order o
    """)
    Page<Order> listAdmin(Pageable pageable);

    // ✅ create(data) → save()

    // ✅ updateStatus
    @Modifying
    @Query("""
        UPDATE Order o
        SET o.status = :status,
            o.updatedAt = :updatedAt
        WHERE o.id = :id
    """)
    void updateStatus(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
