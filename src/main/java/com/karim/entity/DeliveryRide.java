package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.RideStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "delivery_rides", indexes = { @Index(name = "idx_assignment_id", columnList = "assignment_id"),
		@Index(name = "idx_agent_id", columnList = "agent_id"),
		@Index(name = "idx_order_id", columnList = "order_id") })
@Data
@SQLDelete(sql = "UPDATE delivery_rides SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class DeliveryRide {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: assignment_id
	@Column(name = "assignment_id", nullable = false)
	private UUID assignmentId;

	// FK: agent_id
	@Column(name = "agent_id", nullable = false)
	private UUID agentId;

	// FK: order_id
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	// FK: pricing_config_id
	@Column(name = "pricing_config_id")
	private UUID pricingConfigId;

	@Column(name = "start_latitude")
	private float startLatitude;

	@Column(name = "start_longitude")
	private float startLongitude;

	@Column(name = "end_latitude")
	private float endLatitude;

	@Column(name = "end_longitude")
	private float endLongitude;

	@Column(name = "distance_km")
	private float distanceKm;

	@Column(name = "base_amount")
	private float baseAmount;

	@Column(name = "km_amount")
	private float kmAmount;

	@Column(name = "surge_amount")
	private float surgeAmount;

	@Column(name = "total_fare")
	private float totalFare;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RideStatus status;

	@Column(name = "ride_started_at")
	private LocalDateTime rideStartedAt;

	@Column(name = "ride_ended_at")
	private LocalDateTime rideEndedAt;

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
		this.status = RideStatus.STARTED;
		this.rideStartedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}