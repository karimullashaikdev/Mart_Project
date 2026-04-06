package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.ChatStatus;

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
@Table(name = "chat_sessions")
@Data
@SQLDelete(sql = "UPDATE chat_sessions SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class ChatSession {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: order_id
	@Column(name = "order_id")
	private UUID orderId;

	// FK: user_id
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	// FK: agent_id
	@Column(name = "agent_id")
	private UUID agentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ChatStatus status;

	@Column(name = "ai_enabled")
	private boolean aiEnabled = false;

	@Column(name = "opened_at")
	private LocalDateTime openedAt;

	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@Column(name = "last_message_at")
	private LocalDateTime lastMessageAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

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
		this.openedAt = LocalDateTime.now();
		this.status = ChatStatus.ACTIVE;
		this.lastMessageAt = LocalDateTime.now();
	}
}
