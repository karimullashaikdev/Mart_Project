package com.karim.entity;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.karim.enums.AddressLabel;

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
@Table(name = "addresses")
@Data
//@SQLDelete(sql = "UPDATE addresses SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")

public class Address {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(columnDefinition = "CHAR(36)")
	private UUID id;

	// 🔗 Many addresses → One user
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "label")
	private AddressLabel label;

	@Column(name = "line1", nullable = false)
	private String line1;

	@Column(name = "line2")
	private String line2;

	@Column(name = "city", nullable = false)
	private String city;

	@Column(name = "state", nullable = false)
	private String state;

	@Column(name = "pincode", nullable = false)
	private String pincode;

	@Column(name = "latitude")
	private Double latitude;

	@Column(name = "longitude")
	private Double longitude;

	@Column(name = "is_default")
	private Boolean isDefault = false;

	// 🔁 Audit Fields
	@Column(name = "created_by")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID createdBy;

	@Column(name = "updated_by")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID updatedBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
	
	@Column(name = "phone")
	private String phone;

	@Column(name = "landmark")
	private String landmark;

	// ❌ Soft Delete
	@Column(name = "is_deleted")
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	@JdbcTypeCode(Types.VARCHAR)
	private UUID deletedBy;

	// ✅ Lifecycle Hooks
	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
