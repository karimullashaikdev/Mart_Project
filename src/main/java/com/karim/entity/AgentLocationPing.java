package com.karim.entity;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.AgentLocationEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "agent_location_pings", indexes = {
        @Index(name = "idx_assignment_id", columnList = "assignment_id"),
        @Index(name = "idx_agent_id", columnList = "agent_id"),
        @Index(name = "idx_ping_sequence", columnList = "ping_sequence")
})
@Data
@SQLDelete(sql = "UPDATE agent_location_pings SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class AgentLocationPing {

    @Id
    @GeneratedValue
    @JdbcTypeCode(Types.VARCHAR)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    @JdbcTypeCode(Types.VARCHAR)
    private UUID assignmentId;

    @Column(name = "agent_id", nullable = false)
    @JdbcTypeCode(Types.VARCHAR)
    private UUID agentId;

    @Column(name = "ping_sequence", nullable = false)
    private Long pingSequence;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "bearing")
    private Double bearing;

    @Column(name = "altitude_meters")
    private Double altitudeMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AgentLocationEventType eventType;

    @Column(name = "ws_connection_id")
    private String wsConnectionId;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    @JdbcTypeCode(Types.VARCHAR)
    private UUID deletedBy;

    @PrePersist
    public void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}