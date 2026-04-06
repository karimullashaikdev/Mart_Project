package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.ReturnRequest;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    // ✅ findById
    Optional<ReturnRequest> findById(UUID id);

    // ✅ findByNumber
    Optional<ReturnRequest> findByReturnNumber(String returnNumber);

    // ✅ findByUser
    List<ReturnRequest> findByUserId(UUID userId);

    // (filters + pagination will be handled in service using Pageable / Specifications)

    // ✅ findByOrder
    List<ReturnRequest> findByOrderId(UUID orderId);

    // ✅ create(data) → save()

    // ✅ update(id, data) → save() in service

    // ✅ softDelete(id, actorId) → handled in service layer (custom update query if needed)

}