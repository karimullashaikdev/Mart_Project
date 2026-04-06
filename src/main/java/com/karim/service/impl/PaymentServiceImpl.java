package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.karim.dto.InvoiceResponse;
import com.karim.dto.PaymentResponse;
import com.karim.entity.Invoice;
import com.karim.entity.Order;
import com.karim.entity.Otp;
import com.karim.entity.Payment;
import com.karim.entity.Refund;
import com.karim.enums.InvoiceStatus;
import com.karim.enums.OtpPurpose;
import com.karim.enums.PaymentMethod;
import com.karim.enums.PaymentStatus;
import com.karim.enums.RefundMethod;
import com.karim.enums.RefundStatus;
import com.karim.repository.InvoiceRepository;
import com.karim.repository.OrderRepository;
import com.karim.repository.OtpRepository;
import com.karim.repository.PaymentRepository;
import com.karim.repository.RefundRepository;
import com.karim.service.PaymentService;

import jakarta.transaction.Transactional;

public class PaymentServiceImpl implements PaymentService {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final OtpRepository otpRepository;
	private final RefundRepository refundRepository;
	private final InvoiceRepository invoiceRepository;

	public PaymentServiceImpl(OrderRepository orderRepository, PaymentRepository paymentRepository,
			OtpRepository otpRepository, RefundRepository refundRepository,InvoiceRepository invoiceRepository) {
		// TODO Auto-generated constructor stub
		this.orderRepository = orderRepository;
		this.otpRepository = otpRepository;
		this.paymentRepository = paymentRepository;
		this.refundRepository = refundRepository;
		this.invoiceRepository=invoiceRepository;
	}

	@Transactional
	@Override
	public PaymentResponse initiatePayment(UUID orderId, UUID userId, PaymentMethod method, UUID actorId) {

		// 🔍 1. Validate Order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate ownership
		if (!order.getUser().getId().equals(userId)) {
			throw new RuntimeException("User not authorized for this order");
		}

		// ⚠️ 3. Prevent duplicate payment
		paymentRepository.findByOrderId(orderId).ifPresent(p -> {
			if (p.getStatus() == PaymentStatus.SUCCESS) {
				throw new RuntimeException("Order already paid");
			}
		});

		// 🔢 4. Generate payment reference
		String paymentRef = "PAY-" + System.currentTimeMillis();

		// 💰 5. Create payment
		Payment payment = Payment.builder().orderId(orderId).userId(userId).paymentReference(paymentRef).method(method)
				.status(PaymentStatus.PENDING).amount(order.getTotalAmount().floatValue())
				.initiatedAt(LocalDateTime.now()).createdBy(actorId).build();

		Payment savedPayment = paymentRepository.save(payment);

		// 🔐 6. Generate OTP
		String otp = String.valueOf((int) (Math.random() * 900000) + 100000); // 6-digit

		// 🔒 Hash OTP (simple for now)
		String otpHash = Integer.toString(otp.hashCode());

		Otp otpEntity = new Otp();
		otpEntity.setUserId(userId);
		otpEntity.setReferenceId(paymentRef);
		otpEntity.setPurpose(OtpPurpose.PAYMENT_VERIFICATION);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));

		otpRepository.save(otpEntity);

		// 🔄 7. Update payment status
		savedPayment.setStatus(PaymentStatus.OTP_SENT);
		paymentRepository.save(savedPayment);

		// 📤 8. Return response (include OTP for testing)
		return PaymentResponse.builder().paymentId(savedPayment.getId()).paymentReference(paymentRef)
				.status(savedPayment.getStatus().name()).message("OTP sent successfully").otp(otp) // ⚠️ REMOVE in
																									// production
				.build();
	}

	@Transactional
	@Override
	public PaymentResponse sendPaymentOtp(UUID paymentId, UUID userId) {

		// 🔍 1. Fetch payment
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		// ⚠️ 2. Validate ownership
		if (!payment.getUserId().equals(userId)) {
			throw new RuntimeException("User not authorized for this payment");
		}

		// ⚠️ 3. Validate payment status
		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Payment already completed");
		}

		// 🔐 4. Generate OTP
		String otp = String.valueOf((int) (Math.random() * 900000) + 100000);

		// 🔒 Hash OTP
		String otpHash = Integer.toString(otp.hashCode());

		// 🧾 5. Create OTP entity
		Otp otpEntity = new Otp();
		otpEntity.setUserId(userId);
		otpEntity.setReferenceId(payment.getPaymentReference());
		otpEntity.setPurpose(OtpPurpose.PAYMENT_VERIFICATION);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));

		otpRepository.save(otpEntity);

		// 🔄 6. Update payment status
		payment.setStatus(PaymentStatus.OTP_SENT);
		paymentRepository.save(payment);

		// 📤 7. (Future) NotificationService call
		// notificationService.sendOtp(userId, otp);

		// 📦 8. Return response
		return PaymentResponse.builder().paymentId(payment.getId()).paymentReference(payment.getPaymentReference())
				.status(payment.getStatus().name()).message("OTP sent successfully").amount(payment.getAmount())
				.method(payment.getMethod().name()).otp(otp) // ⚠️ remove in production
				.build();
	}

	@Transactional
	@Override
	public PaymentResponse verifyPaymentOtp(UUID paymentId, UUID userId, String otp) {

		// 🔍 1. Fetch payment
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		// ⚠️ 2. Validate ownership
		if (!payment.getUserId().equals(userId)) {
			throw new RuntimeException("User not authorized for this payment");
		}

		// ⚠️ 3. Validate status
		if (payment.getStatus() != PaymentStatus.OTP_SENT) {
			throw new RuntimeException("OTP verification not allowed at this stage");
		}

		// 🔍 4. Fetch latest OTP
		Otp otpEntity = otpRepository
				.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, OtpPurpose.PAYMENT_VERIFICATION)
				.orElseThrow(() -> new RuntimeException("OTP not found"));

		// ⚠️ 5. Validate expired
		if (otpEntity.isExpired() || otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
			otpEntity.setExpired(true);
			otpRepository.save(otpEntity);
			throw new RuntimeException("OTP expired");
		}

		// ⚠️ 6. Validate already used
		if (otpEntity.isUsed()) {
			throw new RuntimeException("OTP already used");
		}

		// ⚠️ 7. Validate attempts
		if (otpEntity.getAttempts() >= otpEntity.getMaxAttempts()) {
			otpEntity.setExpired(true);
			otpRepository.save(otpEntity);
			throw new RuntimeException("Max OTP attempts exceeded");
		}

		// 🔒 8. Match OTP
		String inputHash = Integer.toString(otp.hashCode());

		if (!otpEntity.getOtpHash().equals(inputHash)) {

			otpEntity.setAttempts(otpEntity.getAttempts() + 1);
			otpRepository.save(otpEntity);

			throw new RuntimeException("Invalid OTP");
		}

		// ✅ 9. Mark OTP as used
		otpEntity.setUsed(true);
		otpEntity.setUsedAt(LocalDateTime.now());
		otpRepository.save(otpEntity);

		// 🔄 10. Update payment
		payment.setStatus(PaymentStatus.OTP_VERIFIED);
		payment.setUpdatedBy(userId);

		paymentRepository.save(payment);

		// 📤 11. Return response
		return PaymentResponse.builder().paymentId(payment.getId()).paymentReference(payment.getPaymentReference())
				.status(payment.getStatus().name()).message("OTP verified successfully").amount(payment.getAmount())
				.method(payment.getMethod().name()).build();
	}

	@Transactional
	@Override
	public void confirmPaymentSuccess(UUID paymentId, String gatewayTxnId, String response, UUID actorId) {

		// 🔍 1. Fetch payment
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		// ⚠️ 2. Prevent duplicate success
		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Payment already marked as success");
		}

		// ⚠️ 3. Validate correct stage
		if (payment.getStatus() != PaymentStatus.OTP_VERIFIED) {
			throw new RuntimeException("Payment not verified via OTP");
		}

		// 🔄 4. Update payment details
		payment.setStatus(PaymentStatus.SUCCESS);
		payment.setGatewayTxnId(gatewayTxnId);
		payment.setGatewayResponse(response);
		payment.setCompletedAt(LocalDateTime.now());
		payment.setUpdatedBy(actorId);

		paymentRepository.save(payment);

		// 📄 5. Trigger invoice generation
		invoiceService.generateInvoice(payment.getId());
	}

	@Transactional
	@Override
	public void markPaymentFailed(UUID paymentId, String reason, UUID actorId) {

		// 🔍 1. Fetch payment
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		// ⚠️ 2. Prevent overriding SUCCESS
		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Cannot mark a successful payment as failed");
		}

		// ⚠️ 3. Prevent duplicate failure
		if (payment.getStatus() == PaymentStatus.FAILED) {
			throw new RuntimeException("Payment already marked as failed");
		}

		// 🔄 4. Update payment
		payment.setStatus(PaymentStatus.FAILED);
		payment.setGatewayResponse(reason); // store failure reason
		payment.setUpdatedBy(actorId);

		paymentRepository.save(payment);
	}

	@Override
	public PaymentResponse getPayment(UUID paymentId, UUID userId) {

		// 🔍 1. Fetch payment
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		// 🔐 2. Ownership check
		if (!payment.getUserId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this payment");
		}

		// 📤 3. Map to DTO
		return PaymentResponse.builder().paymentId(payment.getId()).paymentReference(payment.getPaymentReference())
				.status(payment.getStatus().name()).message("Payment fetched successfully").amount(payment.getAmount())
				.method(payment.getMethod().name()).gatewayTxnId(payment.getGatewayTxnId()).build();
	}

	@Override
	public PaymentResponse getPaymentByOrder(UUID orderId) {

		// 🔍 1. Fetch payment
		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new RuntimeException("Payment not found for this order"));

		// 📤 2. Map to DTO
		return PaymentResponse.builder().paymentId(payment.getId()).paymentReference(payment.getPaymentReference())
				.status(payment.getStatus().name()).message("Payment fetched successfully").amount(payment.getAmount())
				.method(payment.getMethod().name()).gatewayTxnId(payment.getGatewayTxnId()).build();
	}

	@Transactional
	@Override
	public void initiateRefund(UUID paymentId, float amount, UUID returnRequestId, UUID actorId) {

		// 🔍 1. Fetch payment
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		// ⚠️ 2. Validate payment status
		if (payment.getStatus() != PaymentStatus.SUCCESS) {
			throw new RuntimeException("Refund allowed only for successful payments");
		}

		// 📊 3. Calculate already refunded amount
		List<Refund> existingRefunds = refundRepository.findByPaymentId(paymentId);

		float alreadyRefunded = existingRefunds.stream()
				.filter(r -> r.getStatus() == RefundStatus.SUCCESS || r.getStatus() == RefundStatus.INITIATED)
				.map(Refund::getAmount).reduce(0f, Float::sum);

		// ⚠️ 4. Validate refund amount
		if (amount <= 0) {
			throw new RuntimeException("Invalid refund amount");
		}

		if (alreadyRefunded + amount > payment.getAmount()) {
			throw new RuntimeException("Refund amount exceeds paid amount");
		}

		// 🔢 5. Generate refund reference
		String refundRef = "REF-" + System.currentTimeMillis();

		// 🧾 6. Create refund entry
		Refund refund = new Refund();
		refund.setPaymentId(paymentId);
		refund.setOrderId(payment.getOrderId());
		refund.setReturnRequestId(returnRequestId);
		refund.setRefundReference(refundRef);
		refund.setAmount(amount);
		refund.setMethod(RefundMethod.ORIGINAL_PAYMENT); // default
		refund.setStatus(RefundStatus.INITIATED);
		refund.setInitiatedBy(actorId);

		refundRepository.save(refund);

		// 🔄 7. Update payment
		payment.setStatus(PaymentStatus.REFUND_INITIATED);

		// Optional: track refunded amount
		float currentRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : 0f;
		payment.setRefundedAmount(currentRefunded + amount);

		paymentRepository.save(payment);
	}

	@Transactional
	@Override
	public void completeRefund(UUID refundId, String gatewayRefundId, UUID actorId) {

		// 🔍 1. Fetch refund
		Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new RuntimeException("Refund not found"));

		// ⚠️ 2. Prevent duplicate completion
		if (refund.getStatus() == RefundStatus.SUCCESS) {
			throw new RuntimeException("Refund already completed");
		}

		// ⚠️ 3. Validate state
		if (refund.getStatus() != RefundStatus.INITIATED && refund.getStatus() != RefundStatus.PROCESSING) {
			throw new RuntimeException("Invalid refund state for completion");
		}

		// 🔄 4. Update refund
		refund.setStatus(RefundStatus.SUCCESS);
		refund.setGatewayRefundId(gatewayRefundId);
		refund.setCompletedAt(LocalDateTime.now());

		refundRepository.save(refund);

		// 🔍 5. Fetch payment
		Payment payment = paymentRepository.findById(refund.getPaymentId())
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		// 🔄 6. (Optional consistency check)
		// You already updated refundedAmount in initiateRefund()
		// So here we just ensure it's not null

		if (payment.getRefundedAmount() == null) {
			payment.setRefundedAmount(refund.getAmount());
		}

		// 💾 7. Save payment (no status change here)
		paymentRepository.save(payment);
	}

	@Transactional
	@Override
	public void failRefund(UUID refundId, String reason, UUID actorId) {

		// 🔍 1. Fetch refund
		Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new RuntimeException("Refund not found"));

		// ⚠️ 2. Prevent invalid transitions
		if (refund.getStatus() == RefundStatus.SUCCESS) {
			throw new RuntimeException("Cannot fail a completed refund");
		}

		if (refund.getStatus() == RefundStatus.FAILED) {
			throw new RuntimeException("Refund already marked as failed");
		}

		// 🔄 3. Update refund
		refund.setStatus(RefundStatus.FAILED);
		refund.setFailureReason(reason);
		refund.setUpdatedAt(LocalDateTime.now());

		refundRepository.save(refund);

		// 🔄 4. Rollback payment refunded amount (IMPORTANT)
		Payment payment = paymentRepository.findById(refund.getPaymentId())
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		float currentRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : 0f;

		payment.setRefundedAmount(Math.max(0f, currentRefunded - refund.getAmount()));
		payment.setUpdatedBy(actorId);

		paymentRepository.save(payment);
	}

	@Transactional
	@Override
	public InvoiceResponse generateInvoice(UUID orderId, UUID paymentId, UUID actorId) {

		// 🔍 1. Validate payment
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (payment.getStatus() != PaymentStatus.SUCCESS) {
			throw new RuntimeException("Invoice can be generated only after successful payment");
		}

		// 🔍 2. Check if invoice already exists
		invoiceRepository.findByOrderId(orderId).ifPresent(inv -> {
			throw new RuntimeException("Invoice already exists for this order");
		});

		// 🔢 3. Generate invoice number
		String invoiceNumber = "INV-" + System.currentTimeMillis();

		// 📄 4. Generate PDF URL (placeholder for now)
		String pdfUrl = "/invoices/" + invoiceNumber + ".pdf";

		// 🧾 5. Create invoice
		Invoice invoice = Invoice.builder().orderId(orderId).paymentId(paymentId).invoiceNumber(invoiceNumber)
				.pdfUrl(pdfUrl).status(InvoiceStatus.GENERATED).retryCount(0).generatedAt(LocalDateTime.now())
				.createdBy(actorId).build();

		invoiceRepository.save(invoice);

		// 📦 6. Return response
		return InvoiceResponse.builder().invoiceId(invoice.getId()).invoiceNumber(invoice.getInvoiceNumber())
				.orderId(orderId).paymentId(paymentId).pdfUrl(pdfUrl).status(invoice.getStatus().name())
				.generatedAt(invoice.getGeneratedAt()).build();
	}
}
