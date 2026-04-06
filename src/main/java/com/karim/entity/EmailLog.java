package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.EmailStatus;
import com.karim.enums.EmailType;

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
@Table(name = "email_logs")
@Data
@SQLDelete(sql = "UPDATE email_logs SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class EmailLog {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: user_id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "reference_id")
	private String referenceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "email_type", nullable = false)
	private EmailType emailType;

	@Column(name = "to_email", nullable = false)
	private String toEmail;

	@Column(name = "subject")
	private String subject;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private EmailStatus status;

	@Column(name = "retry_count")
	private int retryCount = 0;

	@Column(name = "provider_message_id")
	private String providerMessageId;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "queued_at")
	private LocalDateTime queuedAt;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "failed_at")
	private LocalDateTime failedAt;

	@Column(name = "is_deleted")
	private boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	// FK: deleted_by
	@Column(name = "deleted_by")
	private UUID deletedBy;

	// Auto timestamp for queue
	@PrePersist
	public void onCreate() {
		this.queuedAt = LocalDateTime.now();
		this.status = EmailStatus.QUEUED;
	}
}
