package com.karim.repository;

import java.util.List;
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

    Optional<Notification> findById(UUID id);

    Page<Notification> findByUserIdAndIsDeletedFalse(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadFalseAndIsDeletedFalse(UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalseAndIsDeletedFalse(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id")
    void markRead(@Param("id") UUID id);

    // ✅ FIXED
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isDeleted = false")
    void markAllRead(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE Notification n
        SET n.isDeleted = true,
            n.deletedBy = :actorId
        WHERE n.id = :id
    """)
    void softDelete(@Param("id") UUID id, @Param("actorId") UUID actorId);

    List<Notification> findByUserIdAndIsReadFalse(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);
}