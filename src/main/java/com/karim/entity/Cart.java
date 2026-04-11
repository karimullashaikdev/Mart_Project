package com.karim.entity;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cart – one active cart per user at a time. Cart items are embedded as a
 * one-to-many collection.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	/**
	 * Each user has at most ONE active cart (non-deleted). If you allow guest
	 * carts, make this nullable and track by session_id instead.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/**
	 * Denormalised coupon that was applied (optional). Validation is done in
	 * CartService; actual discount is recomputed on checkout.
	 */
	@Column(name = "coupon_code", length = 50)
	private String couponCode;

	// ── soft-delete ────────────────────────────────────────────────────────
	@Column(name = "is_deleted", nullable = false)
	@Builder.Default
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	private UUID deletedBy;

	// ── audit ──────────────────────────────────────────────────────────────
	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// ── items ──────────────────────────────────────────────────────────────
	@OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@Builder.Default
	private List<CartItem> items = new ArrayList<>();

	// ── helpers ────────────────────────────────────────────────────────────
	public void softDelete(UUID actorId) {
		this.isDeleted = true;
		this.deletedAt = LocalDateTime.now();
		this.deletedBy = actorId;
	}

	public BigDecimal getSubtotal() {
		return items.stream().filter(i -> !i.getIsDeleted()).map(CartItem::getLineTotal).reduce(BigDecimal.ZERO,
				BigDecimal::add);
	}
}
