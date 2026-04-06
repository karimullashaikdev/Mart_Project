package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.DeliveryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "delivery_assignments")
@Data
@SQLDelete(sql = "UPDATE delivery_assignments SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class DeliveryAssignment {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: order_id
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	// FK: agent_id
	@Column(name = "agent_id", nullable = false)
	private UUID agentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private DeliveryStatus status;

	@Column(name = "attempt_number")
	private int attemptNumber = 1;

	@Column(name = "delivery_proof_url")
	private String deliveryProofUrl;

	@Column(name = "failure_reason")
	private String failureReason;

	// FK: assigned_by
	@Column(name = "assigned_by")
	private UUID assignedBy;

	@Column(name = "assigned_at")
	private LocalDateTime assignedAt;

	@Column(name = "accepted_at")
	private LocalDateTime acceptedAt;

	@Column(name = "rejected_at")
	private LocalDateTime rejectedAt;

	@Column(name = "picked_up_at")
	private LocalDateTime pickedUpAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

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

	// Auto timestamps
	@PrePersist
	public void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.assignedAt = LocalDateTime.now();
		this.status = DeliveryStatus.ASSIGNED;
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}