package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "razorpay_webhook_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
@SQLDelete(sql = "UPDATE razorpay_webhook_logs SET is_deleted = true, deleted_at = now() WHERE id = ?")
public class RazorpayWebhookLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "event_id")
	private String eventId;

	@Column(name = "event_type")
	private String eventType;

	@Column(name = "account_id")
	private String accountId;

	@Column(name = "signature", columnDefinition = "TEXT")
	private String signature;

	@Column(name = "gateway_order_id")
	private String gatewayOrderId;

	@Column(name = "gateway_payment_id")
	private String gatewayPaymentId;

	@Column(name = "status")
	private String status;

	@Column(name = "payload", columnDefinition = "LONGTEXT")
	private String payload;

	@Column(name = "processed")
	private Boolean processed = false;

	@Column(name = "processing_message", columnDefinition = "TEXT")
	private String processingMessage;

	@Column(name = "received_at")
	private LocalDateTime receivedAt;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "is_deleted")
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
		if (this.receivedAt == null) {
			this.receivedAt = LocalDateTime.now();
		}
		if (this.isDeleted == null) {
			this.isDeleted = false;
		}
		if (this.processed == null) {
			this.processed = false;
		}
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}