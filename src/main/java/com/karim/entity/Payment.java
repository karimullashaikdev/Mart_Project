package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

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
@SQLRestriction("is_deleted = false")
@SQLDelete(sql = "UPDATE payments SET is_deleted = true, deleted_at = now() WHERE id = ?")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID id;

	@Column(name = "order_id")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID orderId;

	@Column(name = "user_id")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID userId;

	@Column(name = "payment_reference", unique = true)
	private String paymentReference;

	@Enumerated(EnumType.STRING)
	@Column(name = "method")
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private PaymentStatus status;

	@Column(name = "amount")
	private Float amount;

	@Column(name = "refunded_amount")
	private Float refundedAmount;

	@Column(name = "gateway_txn_id")
	private String gatewayTxnId;

	@Column(name = "gateway_response", columnDefinition = "TEXT")
	private String gatewayResponse;

	// NEW: Razorpay fields
	@Column(name = "gateway_order_id", unique = true)
	private String gatewayOrderId;

	@Column(name = "gateway_payment_id", unique = true)
	private String gatewayPaymentId;

	@Column(name = "gateway_signature", columnDefinition = "TEXT")
	private String gatewaySignature;

	@Column(name = "gateway_name")
	private String gatewayName;

	@Column(name = "initiated_at")
	private LocalDateTime initiatedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "created_by")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID createdBy;

	@Column(name = "updated_by")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID updatedBy;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "is_deleted")
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID deletedBy;

	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();

		if (this.isDeleted == null) {
			this.isDeleted = false;
		}
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}