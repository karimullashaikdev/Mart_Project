package com.karim.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.karim.enums.AIModel;

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
@Table(name = "ai_chat_contexts", indexes = { @Index(name = "idx_session_id", columnList = "session_id") })
@Data
@SQLDelete(sql = "UPDATE ai_chat_contexts SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class AIChatContext {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: session_id
	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	// System prompt stored as JSONB
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "system_prompt", columnDefinition = "jsonb")
	private Map<String, Object> systemPrompt;

	// Message history stored as JSONB
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "message_history", columnDefinition = "jsonb")
	private List<Map<String, Object>> messageHistory;

	@Enumerated(EnumType.STRING)
	@Column(name = "model", nullable = false)
	private AIModel model;

	@Column(name = "total_tokens_used")
	private Integer totalTokensUsed;

	@Column(name = "estimated_cost_usd")
	private float estimatedCostUsd;

	@Column(name = "last_used_at")
	private LocalDateTime lastUsedAt;

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
		this.lastUsedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}