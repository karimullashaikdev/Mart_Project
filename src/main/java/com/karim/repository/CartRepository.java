package com.karim.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Query("""
            SELECT DISTINCT c FROM Cart c
            LEFT JOIN FETCH c.items i
            LEFT JOIN FETCH i.product
            WHERE c.user.id = :userId
              AND c.isDeleted = false
            """)
    Optional<Cart> findActiveCartByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndIsDeletedFalse(UUID userId);

    @Modifying
    @Query("""
            UPDATE Cart c
               SET c.isDeleted = true,
                   c.deletedAt = :now,
                   c.deletedBy = :actorId
             WHERE c.user.id = :userId
               AND c.isDeleted = false
            """)
    void softDeleteActiveCartByUserId(
            @Param("userId") UUID userId,
            @Param("actorId") UUID actorId,
            @Param("now") LocalDateTime now);
}