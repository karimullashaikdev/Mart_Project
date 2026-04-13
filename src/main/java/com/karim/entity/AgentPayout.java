package com.karim.entity;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.PayoutStatus;

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
@Table(name = "agent_payouts", indexes = { @Index(name = "idx_agent_id", columnList = "agent_id"),
		@Index(name = "idx_status", columnList = "status") })
@Data
@SQLDelete(sql = "UPDATE agent_payouts SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class AgentPayout {

	@Id
	@GeneratedValue
	@JdbcTypeCode(Types.VARCHAR)
	private UUID id;

	// FK: agent_id
	@Column(name = "agent_id", nullable = false)
	@JdbcTypeCode(Types.VARCHAR)
	private UUID agentId;

	@Column(name = "total_amount", nullable = false)
	private Double totalAmount;

	@Column(name = "earnings_count")
	private Integer earningsCount;

	@Column(name = "upi_id")
	private String upiId;

	@Column(name = "account_number")
	private String accountNumber;

	@Column(name = "ifsc_code")
	private String ifscCode;

	@Column(name = "transaction_ref")
	private String transactionRef;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private PayoutStatus status;

	@Column(name = "initiated_at")
	private LocalDateTime initiatedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	// FK: processed_by (admin/system user)
	@Column(name = "processed_by")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID processedBy;

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
		this.initiatedAt = LocalDateTime.now();
		this.status = PayoutStatus.INITIATED;
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
