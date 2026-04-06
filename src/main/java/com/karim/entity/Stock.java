package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.StockStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "stock")
@Data
@SQLDelete(sql = "UPDATE stock SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")

public class Stock {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "CHAR(36)")
	private UUID id;

	// 🔗 One Product → One Stock
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false, unique = true)
	private Product product;

	@Column(name = "quantity_available")
	private Integer quantityAvailable = 0;

	@Column(name = "quantity_reserved")
	private Integer quantityReserved = 0;

	@Column(name = "reorder_level")
	private Integer reorderLevel;

	@Column(name = "reorder_quantity")
	private Integer reorderQuantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private StockStatus status;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "last_updated_at")
	private LocalDateTime lastUpdatedAt;

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
		this.lastUpdatedAt = LocalDateTime.now();
		updateStockStatus();
	}

	@PreUpdate
	public void preUpdate() {
		this.lastUpdatedAt = LocalDateTime.now();
		updateStockStatus();
	}

	// 🔥 Auto status calculation
	private void updateStockStatus() {
		if (quantityAvailable == null || quantityAvailable <= 0) {
			this.status = StockStatus.OUT_OF_STOCK;
		} else if (reorderLevel != null && quantityAvailable <= reorderLevel) {
			this.status = StockStatus.LOW_STOCK;
		} else {
			this.status = StockStatus.IN_STOCK;
		}
	}
}