package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.RefundMethod;
import com.karim.enums.RefundStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(name = "refunds", uniqueConstraints = {
		@UniqueConstraint(name = "uk_refund_reference", columnNames = "refund_reference") })
@Data
@SQLDelete(sql = "UPDATE refunds SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class Refund {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: return_request_id
	@Column(name = "return_request_id", nullable = false)
	private UUID returnRequestId;

	// FK: payment_id
	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	// FK: order_id
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	// UK: refund_reference
	@Column(name = "refund_reference", nullable = false, unique = true)
	private String refundReference;

	@Column(name = "amount", nullable = false)
	private float amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "method", nullable = false)
	private RefundMethod method;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RefundStatus status;

	@Column(name = "gateway_refund_id")
	private String gatewayRefundId;

	@Column(name = "failure_reason")
	private String failureReason;

	// FK: initiated_by
	@Column(name = "initiated_by")
	private UUID initiatedBy;

	@Column(name = "initiated_at")
	private LocalDateTime initiatedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

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
		this.initiatedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
