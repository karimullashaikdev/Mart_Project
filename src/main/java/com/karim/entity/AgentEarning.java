package com.karim.entity;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.EarningStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "agent_earnings", indexes = { @Index(name = "idx_agent_id", columnList = "agent_id"),
		@Index(name = "idx_assignment_id", columnList = "assignment_id"),
		@Index(name = "idx_ride_id", columnList = "ride_id") })
@Data
@SQLDelete(sql = "UPDATE agent_earnings SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class AgentEarning {

	@Id
	@GeneratedValue
	@JdbcTypeCode(Types.VARCHAR)
	private UUID id;

	// FK: agent_id
	@Column(name = "agent_id", nullable = false)
	@JdbcTypeCode(Types.VARCHAR)
	private UUID agentId;

	// FK: assignment_id
	@Column(name = "assignment_id", nullable = false)
	@JdbcTypeCode(Types.VARCHAR)
	private UUID assignmentId;

	// FK: ride_id
	@Column(name = "ride_id")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID rideId;

	@Column(name = "base_earning")
	private float baseEarning;

	@Column(name = "km_earning")
	private float kmEarning;

	@Column(name = "surge_earning")
	private float surgeEarning;

	@Column(name = "incentive_amount")
	private float incentiveAmount;

	@Column(name = "penalty_amount")
	private float penaltyAmount;

	@Column(name = "net_earning")
	private float netEarning;

	@Column(name = "incentive_reason")
	private String incentiveReason;

	@Column(name = "penalty_reason")
	private String penaltyReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private EarningStatus status;

	@Column(name = "earned_at")
	private LocalDateTime earnedAt;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	// FK: approved_by
	@Column(name = "approved_by")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID approvedBy;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

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
	@JdbcTypeCode(Types.VARCHAR)
	private UUID deletedBy;

	// Auto timestamps
	@PrePersist
	public void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.earnedAt = LocalDateTime.now();
		this.status = EarningStatus.PENDING;
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
