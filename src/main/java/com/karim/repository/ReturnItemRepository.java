package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.ReturnItem;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, UUID> {

    // ✅ findById
    Optional<ReturnItem> findById(UUID id);

    // ✅ findByReturn
    List<ReturnItem> findByReturnRequestId(UUID returnRequestId);

    // ✅ bulkCreate(items) → handled via saveAll()

    // ✅ softDelete(id, actorId) → handled in service layer (custom logic if required)

}
