package com.karim.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_payout_items",
       indexes = {
           @Index(name = "idx_payout_id", columnList = "payout_id"),
           @Index(name = "idx_earning_id", columnList = "earning_id")
       })
@Data
@SQLDelete(sql = "UPDATE agent_payout_items SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class AgentPayoutItem {

    @Id
    @GeneratedValue
    private UUID id;

    // FK: payout_id
    @Column(name = "payout_id", nullable = false)
    private UUID payoutId;

    // FK: earning_id
    @Column(name = "earning_id", nullable = false)
    private UUID earningId;

    @Column(name = "amount", nullable = false)
    private float amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // FK: deleted_by
    @Column(name = "deleted_by")
    private UUID deletedBy;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}