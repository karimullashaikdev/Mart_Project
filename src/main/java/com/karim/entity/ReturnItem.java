package com.karim.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.ReturnCondition;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "return_items")
@Data
@SQLDelete(sql = "UPDATE return_items SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class ReturnItem {

    @Id
    @GeneratedValue
    private UUID id;

    // FK: return_request_id
    @Column(name = "return_request_id", nullable = false)
    private UUID returnRequestId;

    // FK: order_item_id
    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    // FK: product_id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity_returned", nullable = false)
    private int quantityReturned;

    @Column(name = "reason")
    private String reason;

    // ✅ renamed to avoid MySQL reserved keyword
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false)
    private ReturnCondition condition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // FK: deleted_by
    @Column(name = "deleted_by")
    private UUID deletedBy;

    // ✅ Auto timestamps
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}