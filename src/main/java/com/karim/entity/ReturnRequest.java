package com.karim.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.ReturnReason;
import com.karim.enums.ReturnStatus;
import com.karim.enums.ReturnType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(name = "return_requests", uniqueConstraints = {
		@UniqueConstraint(name = "uk_return_number", columnNames = "return_number") })
@Data
@SQLDelete(sql = "UPDATE return_requests SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class ReturnRequest {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "return_number", nullable = false, unique = true)
	private String returnNumber;

	// FK: order_id
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	// FK: user_id
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false)
	private ReturnReason reason;

	@Column(name = "description")
	private String description;

	@Lob
	@Column(columnDefinition = "JSON")
	private List<String> images;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ReturnStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "return_type", nullable = false)
	private ReturnType returnType;

	@Column(name = "refund_amount")
	private float refundAmount;

	@Column(name = "rejection_reason")
	private String rejectionReason;

	// FK: reviewed_by
	@Column(name = "reviewed_by")
	private UUID reviewedBy;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

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
		this.requestedAt = LocalDateTime.now();
		this.status = ReturnStatus.PENDING;
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
