package com.karim.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.AuditLog;

public interface AuditRepository extends JpaRepository<AuditLog, UUID> {

    // ✅ create(data) → handled by save()

    // ✅ findByEntity(entityType, entityId, pag)
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    // ✅ findByUser(userId, filters, pag)
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    // (Optional: if you want filtering by action/type/date etc. add more methods)

    // Example:
    // Page<AuditLog> findByUserIdAndAction(UUID userId, String action, Pageable pageable);

    // ✅ listAdmin(filters, pagination)
    // You can reuse built-in pagination with custom filters via derived queries or @Query
    // Example generic listing:
    Page<AuditLog> findAll(Pageable pageable);

}
