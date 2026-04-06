package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    // ✅ findById
    Optional<Invoice> findById(UUID id);

    // ✅ findByOrder
    Optional<Invoice> findByOrderId(UUID orderId);

    // ✅ findByNumber
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    // ✅ create(data) → handled by save()

    // ✅ update(id, data) → handled by save() in service

}
