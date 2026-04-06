package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.AvailabilityStatus;
import com.karim.enums.VehicleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "delivery_agents")
@Data
@SQLDelete(sql = "UPDATE delivery_agents SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class DeliveryAgent {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: user_id
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "vehicle_type")
	private VehicleType vehicleType;

	@Column(name = "vehicle_number")
	private String vehicleNumber;

	@Column(name = "license_number")
	private String licenseNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "availability_status")
	private AvailabilityStatus availabilityStatus = AvailabilityStatus.OFFLINE;

	@Column(name = "is_verified")
	private boolean isVerified = false;

	@Column(name = "rating_avg")
	private float ratingAvg = 0.0f;

	@Column(name = "total_deliveries")
	private int totalDeliveries = 0;

	@Column(name = "total_earnings_all_time")
	private float totalEarningsAllTime = 0.0f;

	@Column(name = "wallet_balance")
	private float walletBalance = 0.0f;

	// FK: updated_by
	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "is_deleted")
	private boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	// FK: deleted_by
	@Column(name = "deleted_by")
	private UUID deletedBy;

	// Auto timestamps
	@PrePersist
	public void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
