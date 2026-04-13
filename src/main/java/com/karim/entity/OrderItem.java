package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.karim.enums.OrderItemStatus;

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
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "order_items")
@Data
@SQLDelete(sql = "UPDATE order_items SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")

public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "CHAR(36)")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID id;

	// 🔗 Many Items → One Order
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	// 🔗 Many Items → One Product
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@Column(name = "unit_price")
	private Double unitPrice;

	@Column(name = "tax_percent")
	private Double taxPercent;

	@Column(name = "line_total")
	private Double lineTotal;

	@Enumerated(EnumType.STRING)
	@Column(name = "item_status")
	private OrderItemStatus itemStatus;

	// 🔁 Audit Fields
	@Column(name = "created_by")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	// ❌ Soft Delete
	@Column(name = "is_deleted")
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private UUID deletedBy;

	// ✅ Lifecycle Hooks
	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();

		if (this.itemStatus == null) {
			this.itemStatus = OrderItemStatus.ACTIVE;
		}

		// 🔥 Auto calculation
		if (this.unitPrice != null && this.quantity != null) {
			double base = this.unitPrice * this.quantity;
			double tax = (this.taxPercent != null) ? (base * this.taxPercent / 100) : 0;
			this.lineTotal = base + tax;
		}
	}
}
