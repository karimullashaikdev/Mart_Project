package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.OtpPurpose;

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
@Table(name = "otps")
@Data
@SQLDelete(sql = "UPDATE otps SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class Otp {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: user_id
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "reference_id", nullable = false)
	private String referenceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false)
	private OtpPurpose purpose;

	@Column(name = "otp_hash", nullable = false)
	private String otpHash;

	@Column(name = "attempts")
	private int attempts = 0;

	@Column(name = "max_attempts")
	private int maxAttempts = 3;

	@Column(name = "is_used")
	private boolean isUsed = false;

	@Column(name = "is_expired")
	private boolean isExpired = false;

	@Column(name = "ip_address")
	private String ipAddress;

	@Column(name = "device_fingerprint")
	private String deviceFingerprint;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "used_at")
	private LocalDateTime usedAt;

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
