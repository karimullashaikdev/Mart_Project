package com.karim.entity;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single line-item inside a Cart. selling_price is snapshotted at the moment
 * the item is added so the cart total stays stable while the user shops.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uq_cart_product", columnNames = { "cart_id",
		"product_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@JdbcTypeCode(Types.VARCHAR)
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cart_id", nullable = false)
	private Cart cart;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	/**
	 * Snapshotted selling price at add-time. Updated if the user explicitly
	 * refreshes or re-adds the item.
	 */
	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	// ── soft-delete ────────────────────────────────────────────────────────
	@Column(name = "is_deleted", nullable = false)
	@Builder.Default
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID deletedBy;

	// ── audit ──────────────────────────────────────────────────────────────
	@Column(name = "created_by", nullable = false, updatable = false)
	@JdbcTypeCode(Types.VARCHAR)
	private UUID createdBy;

	@Column(name = "updated_by")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID updatedBy;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// ── helper ─────────────────────────────────────────────────────────────
	public BigDecimal getLineTotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

	public void softDelete(UUID actorId) {
		this.isDeleted = true;
		this.deletedAt = LocalDateTime.now();
		this.deletedBy = actorId;
	}
}