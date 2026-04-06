package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // ✅ findById
    Optional<Payment> findById(UUID id);

    // ✅ findByOrder
    Optional<Payment> findByOrderId(UUID orderId);

    // ✅ findByReference
    Optional<Payment> findByPaymentReference(String paymentReference);

    // ✅ create(data) → save()

    // ✅ update(id, data) → handled via save() in service

}