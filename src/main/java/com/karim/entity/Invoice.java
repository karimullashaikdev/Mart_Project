package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.karim.enums.InvoiceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

// ✅ Soft delete filter
@SQLRestriction("is_deleted = false")

// ✅ Soft delete operation
@SQLDelete(sql = "UPDATE invoices SET is_deleted = true, deleted_at = now() WHERE id = ?")
public class Invoice {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	// 🔗 Foreign Keys
	@Column(name = "order_id")
	private UUID orderId;

	@Column(name = "payment_id")
	private UUID paymentId;

	// 🔑 Unique invoice number
	@Column(name = "invoice_number", unique = true)
	private String invoiceNumber;

	// 📄 PDF URL (stored in S3 / CDN / server)
	@Column(name = "pdf_url")
	private String pdfUrl;

	// 📊 Invoice status
	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private InvoiceStatus status;

	// 🔁 Retry handling
	@Column(name = "retry_count")
	private Integer retryCount;

	// ⏱️ Timeline
	@Column(name = "generated_at")
	private LocalDateTime generatedAt;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	// 👤 Audit fields
	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// 🗑️ Soft delete fields
	@Column(name = "is_deleted")
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "deleted_by")
	private UUID deletedBy;

	// ✅ Auto timestamps
	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();

		if (this.isDeleted == null)
			this.isDeleted = false;
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}