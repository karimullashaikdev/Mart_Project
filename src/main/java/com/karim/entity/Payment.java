package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.PaymentMethod;
import com.karim.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

// ✅ Soft delete filter
@SQLRestriction("is_deleted = false")

// ✅ Soft delete operation
@SQLDelete(sql = "UPDATE payments SET is_deleted = true, deleted_at = now() WHERE id = ?")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	// 🔗 Foreign Keys (kept as UUID for flexibility)
	@Column(name = "order_id")
	private UUID orderId;

	@Column(name = "user_id")
	private UUID userId;

	// 🔑 Unique payment reference
	@Column(name = "payment_reference", unique = true)
	private String paymentReference;

	// 💳 Payment method
	@Enumerated(EnumType.STRING)
	@Column(name = "method")
	private PaymentMethod method;

	// 📊 Payment status
	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private PaymentStatus status;

	// 💰 Amount details
	@Column(name = "amount")
	private Float amount;

	@Column(name = "refunded_amount")
	private Float refundedAmount;

	// 🏦 Gateway details
	@Column(name = "gateway_txn_id")
	private String gatewayTxnId;

	@Column(name = "gateway_response", columnDefinition = "TEXT")
	private String gatewayResponse;

	// ⏱️ Timeline
	@Column(name = "initiated_at")
	private LocalDateTime initiatedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	// 👤 Audit fields
	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// 🗑️ Soft delete fields
	@Column(name = "is_deleted")
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	private UUID deletedBy;

	// ✅ Auto timestamps
	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();

		if (this.isDeleted == null)
			this.isDeleted = false;
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
