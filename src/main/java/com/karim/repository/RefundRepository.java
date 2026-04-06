package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.Refund;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    // ✅ findById
    Optional<Refund> findById(UUID id);

    // ✅ findByOrder
    List<Refund> findByOrderId(UUID orderId);

    // ✅ findByPayment
    List<Refund> findByPaymentId(UUID paymentId);

    // ✅ findByReference
    Optional<Refund> findByReference(String reference);

    // ✅ create(data) → handled by save()

    // ✅ update(id, data) → handled by save() in service

}
