package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.StockTransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "stock_transactions")
@Data
@SQLDelete(sql = "UPDATE stock_transactions SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class StockTransaction {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: product_id
	@Column(name = "product_id", nullable = false)
	private UUID productId;

	// FK: order_id
	@Column(name = "order_id")
	private UUID orderId;

	// FK: return_request_id
	@Column(name = "return_request_id")
	private UUID returnRequestId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false)
	private StockTransactionType type;

	@Column(name = "quantity_delta", nullable = false)
	private int quantityDelta;

	@Column(name = "quantity_before", nullable = false)
	private int quantityBefore;

	@Column(name = "quantity_after", nullable = false)
	private int quantityAfter;

	@Column(name = "reason")
	private String reason;

	// FK: created_by
	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "is_deleted")
	private boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	// FK: deleted_by
	@Column(name = "deleted_by")
	private UUID deletedBy;

	// Auto timestamp
	@PrePersist
	public void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
