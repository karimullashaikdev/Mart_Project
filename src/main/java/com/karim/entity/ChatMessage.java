package com.karim.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.karim.enums.MessageType;
import com.karim.enums.SenderType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "chat_messages", indexes = { @Index(name = "idx_session_id", columnList = "session_id"),
		@Index(name = "idx_sender_id", columnList = "sender_id") })
@Data
@SQLDelete(sql = "UPDATE chat_messages SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class ChatMessage {

	@Id
	@GeneratedValue
	private UUID id;

	// FK: session_id
	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	// FK: sender_id
	@Column(name = "sender_id", nullable = false)
	private UUID senderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "sender_type", nullable = false)
	private SenderType senderType;

	@Enumerated(EnumType.STRING)
	@Column(name = "message_type", nullable = false)
	private MessageType messageType;

	@Column(name = "content")
	private String content;

	@Column(name = "media_url")
	private String mediaUrl;

	@Column(name = "is_read")
	private boolean isRead = false;

	@Column(name = "is_deleted")
	private boolean isDeleted = false;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	// FK: deleted_by
	@Column(name = "deleted_by")
	private UUID deletedBy;

	// Auto timestamps
	@PrePersist
	public void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.sentAt = LocalDateTime.now();
	}
}