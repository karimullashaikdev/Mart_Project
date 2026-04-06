package com.karim.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tracking_sessions",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_ws_token", columnNames = "ws_token")
       })
@Data
@SQLDelete(sql = "UPDATE tracking_sessions SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class TrackingSession {

    @Id
    @GeneratedValue
    private UUID id;

    // FK: assignment_id
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    // FK: user_id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ws_token", nullable = false, unique = true)
    private String wsToken;

    @Column(name = "ws_connection_id")
    private String wsConnectionId;

    @Column(name = "last_ping_sequence")
    private Long lastPingSequence;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_ping_at")
    private LocalDateTime lastPingAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // FK: deleted_by
    @Column(name = "deleted_by")
    private UUID deletedBy;

    // Auto timestamps
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastPingAt = LocalDateTime.now();
        this.isActive = true;
    }
}
