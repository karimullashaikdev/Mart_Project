package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // ✅ findById
    Optional<Notification> findById(UUID id);

    // ✅ findByUser with pagination + filters (example: unread filter)
    Page<Notification> findByUserIdAndIsDeletedFalse(
            UUID userId,
            Pageable pageable
    );

    // (Optional) if you want unread filter
    Page<Notification> findByUserIdAndIsReadFalseAndIsDeletedFalse(
            UUID userId,
            Pageable pageable
    );

    // ✅ countUnread
    long countByUserIdAndIsReadFalseAndIsDeletedFalse(UUID userId);

    // ✅ create(data) → save()

    // ✅ markRead(id)
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id")
    void markRead(@Param("id") UUID id);

    // ✅ markAllRead(userId)
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isDeleted = false")
    void markAllRead(@Param("userId") UUID userId);

    // ✅ softDelete(id, actorId)
    @Modifying
    @Transactional
    @Query("""
        UPDATE Notification n 
        SET n.isDeleted = true, n.deletedBy = :actorId 
        WHERE n.id = :id
    """)
    void softDelete(@Param("id") UUID id, @Param("actorId") UUID actorId);

}
