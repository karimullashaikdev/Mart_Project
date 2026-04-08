package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.karim.enums.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "orders")
@Data
@SQLDelete(sql = "UPDATE orders SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")

public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "CHAR(36)")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID id;

	@Column(name = "order_number", unique = true, nullable = false, updatable = false)
	private String orderNumber;

	// 🔗 Many Orders → One User
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 🔗 Delivery Address
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "address_id", nullable = false)
	private Address address;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private OrderStatus status;

	// 💰 Pricing
	@Column(name = "subtotal")
	private Double subtotal;

	@Column(name = "tax_amount")
	private Double taxAmount;

	@Column(name = "delivery_fee")
	private Double deliveryFee;

	@Column(name = "discount_amount")
	private Double discountAmount;

	@Column(name = "total_amount")
	private Double totalAmount;

	// 📝 Notes
	@Column(name = "customer_notes")
	private String customerNotes;

	@Column(name = "cancellation_reason")
	private String cancellationReason;

	// ⏱️ Status timestamps
	@Column(name = "placed_at")
	private LocalDateTime placedAt;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	@Column(name = "dispatched_at")
	private LocalDateTime dispatchedAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	// 🔁 Audit Fields
	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// ❌ Soft Delete
	@Column(name = "is_deleted")
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	private UUID deletedBy;

	// ✅ Lifecycle Hooks
	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();

		if (this.status == null) {
			this.status = OrderStatus.PENDING;
		}

		if (this.placedAt == null) {
			this.placedAt = LocalDateTime.now();
		}
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}