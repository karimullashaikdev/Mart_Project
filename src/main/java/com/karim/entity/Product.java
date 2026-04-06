package com.karim.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.UnitType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
@Table(name = "products")
@Data
@SQLDelete(sql = "UPDATE products SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")

public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "CHAR(36)")
	private UUID id;

	// 🔗 Many Products → One Category
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "slug", nullable = false, unique = true)
	private String slug;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "sku", nullable = false, unique = true)
	private String sku;

	@Column(name = "barcode")
	private String barcode;

	// 💰 Pricing
	@Column(name = "mrp")
	private Double mrp;

	@Column(name = "selling_price")
	private Double sellingPrice;

	@Column(name = "tax_percent")
	private Double taxPercent;

	// 📦 Unit details
	@Enumerated(EnumType.STRING)
	@Column(name = "unit")
	private UnitType unit;

	@Column(name = "unit_value")
	private Double unitValue;

	// 🖼️ Images (separate table for scalability)
	@ElementCollection
	@CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "image_url")
	private List<String> images;

	@Column(name = "is_active")
	private Boolean isActive = true;

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

		if (this.isActive == null) {
			this.isActive = true;
		}
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}