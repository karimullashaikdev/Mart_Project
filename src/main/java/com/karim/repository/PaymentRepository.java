package com.karim.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karim.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findById(UUID id);

	Optional<Payment> findByOrderId(UUID orderId);

	Optional<Payment> findByPaymentReference(String paymentReference);

	Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

	Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);
}
