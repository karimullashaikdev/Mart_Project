package com.karim.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.config.RazorpayConfig;
import com.karim.dto.InvoiceResponse;
import com.karim.dto.PaymentResponse;
import com.karim.dto.RazorpayWebhookLogResponse;
import com.karim.entity.Invoice;
import com.karim.entity.Order;
import com.karim.entity.Otp;
import com.karim.entity.Payment;
import com.karim.entity.RazorpayWebhookLog;
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
import com.karim.repository.RazorpayWebhookLogRepository;
import com.karim.repository.RefundRepository;
import com.karim.service.PaymentService;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;

@Service
public class PaymentServiceImpl implements PaymentService {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final OtpRepository otpRepository;
	private final RefundRepository refundRepository;
	private final InvoiceRepository invoiceRepository;
	private final RazorpayClient razorpayClient;
	private final RazorpayConfig razorpayConfig;
	private final RazorpayWebhookLogRepository razorpayWebhookLogRepository;
	private final OrderNotificationService orderNotificationService;

	public PaymentServiceImpl(OrderRepository orderRepository, PaymentRepository paymentRepository,
			OtpRepository otpRepository, RefundRepository refundRepository, InvoiceRepository invoiceRepository,
			RazorpayClient razorpayClient, RazorpayConfig razorpayConfig,
			RazorpayWebhookLogRepository razorpayWebhookLogRepository,
			OrderNotificationService orderNotificationService) {
		this.orderRepository = orderRepository;
		this.otpRepository = otpRepository;
		this.paymentRepository = paymentRepository;
		this.refundRepository = refundRepository;
		this.invoiceRepository = invoiceRepository;
		this.razorpayClient = razorpayClient;
		this.razorpayConfig = razorpayConfig;
		this.razorpayWebhookLogRepository = razorpayWebhookLogRepository;
		this.orderNotificationService = orderNotificationService;
	}

	@Transactional
	@Override
	public PaymentResponse initiatePayment(UUID orderId, UUID userId, PaymentMethod method, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (!order.getUser().getId().equals(userId)) {
			throw new RuntimeException("User not authorized for this order");
		}

		paymentRepository.findByOrderId(orderId).ifPresent(p -> {
			if (p.getStatus() == PaymentStatus.SUCCESS) {
				throw new RuntimeException("Order already paid");
			}
		});

		String paymentRef = "PAY-" + System.currentTimeMillis();

		Payment payment = Payment.builder().orderId(orderId).userId(userId).paymentReference(paymentRef).method(method)
				.amount(order.getTotalAmount().floatValue()).initiatedAt(LocalDateTime.now()).createdBy(actorId)
				.gatewayName(isRazorpayMethod(method) ? "RAZORPAY" : "INTERNAL").build();

		// Razorpay flow: no OTP
		if (isRazorpayMethod(method)) {
			try {
				long amountInPaise = convertToPaise(order.getTotalAmount());

				JSONObject orderRequest = new JSONObject();
				orderRequest.put("amount", amountInPaise);
				orderRequest.put("currency", "INR");
				orderRequest.put("receipt", paymentRef);

				JSONObject notes = new JSONObject();
				notes.put("orderId", orderId.toString());
				notes.put("userId", userId.toString());
				notes.put("paymentReference", paymentRef);
				orderRequest.put("notes", notes);

				com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

				payment.setGatewayOrderId(razorpayOrder.get("id"));
				payment.setStatus(PaymentStatus.PENDING);
				payment.setGatewayResponse(razorpayOrder.toString());

				Payment savedPayment = paymentRepository.save(payment);

				return PaymentResponse.builder().paymentId(savedPayment.getId())
						.paymentReference(savedPayment.getPaymentReference()).status(savedPayment.getStatus().name())
						.message("Razorpay order created successfully").amount(savedPayment.getAmount())
						.method(savedPayment.getMethod().name()).gatewayName(savedPayment.getGatewayName())
						.gatewayKeyId(razorpayConfig.getKeyId()).gatewayOrderId(savedPayment.getGatewayOrderId())
						.build();

			} catch (Exception e) {
				throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage(), e);
			}
		}

		// Non-Razorpay flow (your old OTP logic)
		payment.setStatus(PaymentStatus.PENDING);
		Payment savedPayment = paymentRepository.save(payment);

		String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
		String otpHash = Integer.toString(otp.hashCode());

		Otp otpEntity = new Otp();
		otpEntity.setUserId(userId);
		otpEntity.setReferenceId(paymentRef);
		otpEntity.setPurpose(OtpPurpose.PAYMENT_VERIFICATION);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));

		otpRepository.save(otpEntity);

		savedPayment.setStatus(PaymentStatus.OTP_SENT);
		paymentRepository.save(savedPayment);

		return PaymentResponse.builder().paymentId(savedPayment.getId()).paymentReference(paymentRef)
				.status(savedPayment.getStatus().name()).message("OTP sent successfully")
				.amount(savedPayment.getAmount()).method(savedPayment.getMethod().name()).otp(otp) // remove in
																									// production
				.gatewayName(savedPayment.getGatewayName()).build();
	}

	@Transactional
	@Override
	public PaymentResponse sendPaymentOtp(UUID paymentId, UUID userId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (!payment.getUserId().equals(userId)) {
			throw new RuntimeException("User not authorized for this payment");
		}

		if (isRazorpayMethod(payment.getMethod())) {
			throw new RuntimeException("OTP is not used for Razorpay payments");
		}

		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Payment already completed");
		}

		String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
		String otpHash = Integer.toString(otp.hashCode());

		Otp otpEntity = new Otp();
		otpEntity.setUserId(userId);
		otpEntity.setReferenceId(payment.getPaymentReference());
		otpEntity.setPurpose(OtpPurpose.PAYMENT_VERIFICATION);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));

		otpRepository.save(otpEntity);

		payment.setStatus(PaymentStatus.OTP_SENT);
		paymentRepository.save(payment);

		return PaymentResponse.builder().paymentId(payment.getId()).paymentReference(payment.getPaymentReference())
				.status(payment.getStatus().name()).message("OTP sent successfully").amount(payment.getAmount())
				.method(payment.getMethod().name()).otp(otp) // remove in production
				.gatewayName(payment.getGatewayName()).build();
	}

	@Transactional
	@Override
	public PaymentResponse verifyPaymentOtp(UUID paymentId, UUID userId, String otp) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (!payment.getUserId().equals(userId)) {
			throw new RuntimeException("User not authorized for this payment");
		}

		if (isRazorpayMethod(payment.getMethod())) {
			throw new RuntimeException("OTP verification is not allowed for Razorpay payments");
		}

		if (payment.getStatus() != PaymentStatus.OTP_SENT) {
			throw new RuntimeException("OTP verification not allowed at this stage");
		}

		Otp otpEntity = otpRepository
				.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, OtpPurpose.PAYMENT_VERIFICATION)
				.orElseThrow(() -> new RuntimeException("OTP not found"));

		if (otpEntity.isExpired() || otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
			otpEntity.setExpired(true);
			otpRepository.save(otpEntity);
			throw new RuntimeException("OTP expired");
		}

		if (otpEntity.isUsed()) {
			throw new RuntimeException("OTP already used");
		}

		if (otpEntity.getAttempts() >= otpEntity.getMaxAttempts()) {
			otpEntity.setExpired(true);
			otpRepository.save(otpEntity);
			throw new RuntimeException("Max OTP attempts exceeded");
		}

		String inputHash = Integer.toString(otp.hashCode());

		if (!otpEntity.getOtpHash().equals(inputHash)) {
			otpEntity.setAttempts(otpEntity.getAttempts() + 1);
			otpRepository.save(otpEntity);
			throw new RuntimeException("Invalid OTP");
		}

		otpEntity.setUsed(true);
		otpEntity.setUsedAt(LocalDateTime.now());
		otpRepository.save(otpEntity);

		payment.setStatus(PaymentStatus.OTP_VERIFIED);
		payment.setUpdatedBy(userId);
		paymentRepository.save(payment);

		return PaymentResponse.builder().paymentId(payment.getId()).paymentReference(payment.getPaymentReference())
				.status(payment.getStatus().name()).message("OTP verified successfully").amount(payment.getAmount())
				.method(payment.getMethod().name()).gatewayName(payment.getGatewayName()).build();
	}

	@Transactional
	@Override
	public PaymentResponse verifyRazorpayPayment(UUID paymentId, UUID userId, String razorpayOrderId,
			String razorpayPaymentId, String razorpaySignature, UUID actorId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (!payment.getUserId().equals(userId)) {
			throw new RuntimeException("User not authorized for this payment");
		}

		if (!isRazorpayMethod(payment.getMethod())) {
			throw new RuntimeException("This payment is not a Razorpay payment");
		}

		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Payment already verified and marked success");
		}

		if (payment.getGatewayOrderId() == null || payment.getGatewayOrderId().isBlank()) {
			throw new RuntimeException("Gateway order id missing for this payment");
		}

		if (!payment.getGatewayOrderId().equals(razorpayOrderId)) {
			throw new RuntimeException("Razorpay order id mismatch");
		}

		try {
			JSONObject options = new JSONObject();
			options.put("razorpay_order_id", payment.getGatewayOrderId());
			options.put("razorpay_payment_id", razorpayPaymentId);
			options.put("razorpay_signature", razorpaySignature);

			boolean valid = Utils.verifyPaymentSignature(options, razorpayConfig.getKeySecret());

			if (!valid) {
				payment.setStatus(PaymentStatus.FAILED);
				payment.setGatewayResponse("Invalid Razorpay signature");
				payment.setUpdatedBy(actorId);
				paymentRepository.save(payment);
				throw new RuntimeException("Invalid Razorpay signature");
			}

			payment.setGatewayPaymentId(razorpayPaymentId);
			payment.setGatewaySignature(razorpaySignature);
			payment.setGatewayTxnId(razorpayPaymentId);
			payment.setStatus(PaymentStatus.SUCCESS);
			payment.setCompletedAt(LocalDateTime.now());
			payment.setUpdatedBy(actorId);
			payment.setGatewayResponse("Razorpay payment verified successfully");

			Payment updated = paymentRepository.save(payment);

			Order order = orderRepository.findById(updated.getOrderId())
					.orElseThrow(() -> new RuntimeException("Order not found for this payment"));

			// move order from PENDING to CONFIRMED only after successful payment
			if (order.getStatus() == com.karim.enums.OrderStatus.PENDING) {
				order.setStatus(com.karim.enums.OrderStatus.CONFIRMED);
				order.setConfirmedAt(LocalDateTime.now());
				order.setUpdatedBy(actorId);
				orderRepository.save(order);
				orderNotificationService.notifyNewOrder(order);
			}

			return PaymentResponse.builder().paymentId(updated.getId()).paymentReference(updated.getPaymentReference())
					.status(updated.getStatus().name()).message("Razorpay payment verified successfully")
					.amount(updated.getAmount()).method(updated.getMethod().name())
					.gatewayName(updated.getGatewayName()).gatewayOrderId(updated.getGatewayOrderId())
					.gatewayPaymentId(updated.getGatewayPaymentId()).gatewaySignature(updated.getGatewaySignature())
					.gatewayTxnId(updated.getGatewayTxnId()).build();

		} catch (Exception e) {
			throw new RuntimeException("Payment verification failed: " + e.getMessage(), e);
		}
	}

	@Transactional
	@Override
	public void confirmPaymentSuccess(UUID paymentId, String gatewayTxnId, String response, UUID actorId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Payment already marked as success");
		}

		if (!isRazorpayMethod(payment.getMethod()) && payment.getStatus() != PaymentStatus.OTP_VERIFIED) {
			throw new RuntimeException("Payment not verified via OTP");
		}

		payment.setStatus(PaymentStatus.SUCCESS);
		payment.setGatewayTxnId(gatewayTxnId);
		payment.setGatewayResponse(response);
		payment.setCompletedAt(LocalDateTime.now());
		payment.setUpdatedBy(actorId);
		paymentRepository.save(payment);

		// ✅ ADD BELOW — confirm order and notify delivery for OTP-based payments
		orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
			if (order.getStatus() == com.karim.enums.OrderStatus.PENDING) {
				order.setStatus(com.karim.enums.OrderStatus.CONFIRMED);
				order.setConfirmedAt(LocalDateTime.now());
				order.setUpdatedBy(actorId);
				orderRepository.save(order);
				orderNotificationService.notifyNewOrder(order); // ✅ NOTIFY HERE
			}
		});
	}

	@Transactional
	@Override
	public void markPaymentFailed(UUID paymentId, String reason, UUID actorId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Cannot mark a successful payment as failed");
		}

		if (payment.getStatus() == PaymentStatus.FAILED) {
			throw new RuntimeException("Payment already marked as failed");
		}

		payment.setStatus(PaymentStatus.FAILED);
		payment.setGatewayResponse(reason);
		payment.setUpdatedBy(actorId);

		paymentRepository.save(payment);
	}

	@Override
	public PaymentResponse getPayment(UUID paymentId, UUID userId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (!payment.getUserId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this payment");
		}

		return mapPaymentResponse(payment, "Payment fetched successfully");
	}

	@Override
	public PaymentResponse getPaymentByOrder(UUID orderId) {

		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new RuntimeException("Payment not found for this order"));

		return mapPaymentResponse(payment, "Payment fetched successfully");
	}

	@Transactional
	@Override
	public void initiateRefund(UUID paymentId, float amount, UUID returnRequestId, UUID actorId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (payment.getStatus() != PaymentStatus.SUCCESS) {
			throw new RuntimeException("Refund allowed only for successful payments");
		}

		List<Refund> existingRefunds = refundRepository.findByPaymentId(paymentId);

		float alreadyRefunded = existingRefunds.stream()
				.filter(r -> r.getStatus() == RefundStatus.SUCCESS || r.getStatus() == RefundStatus.INITIATED)
				.map(Refund::getAmount).reduce(0f, Float::sum);

		if (amount <= 0) {
			throw new RuntimeException("Invalid refund amount");
		}

		if (alreadyRefunded + amount > payment.getAmount()) {
			throw new RuntimeException("Refund amount exceeds paid amount");
		}

		String refundRef = "REF-" + System.currentTimeMillis();

		Refund refund = new Refund();
		refund.setPaymentId(paymentId);
		refund.setOrderId(payment.getOrderId());
		refund.setReturnRequestId(returnRequestId);
		refund.setRefundReference(refundRef);
		refund.setAmount(amount);
		refund.setMethod(RefundMethod.ORIGINAL_PAYMENT);
		refund.setStatus(RefundStatus.INITIATED);
		refund.setInitiatedBy(actorId);

		refundRepository.save(refund);

		payment.setStatus(PaymentStatus.REFUND_INITIATED);

		float currentRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : 0f;
		payment.setRefundedAmount(currentRefunded + amount);

		paymentRepository.save(payment);
	}

	@Transactional
	@Override
	public void completeRefund(UUID refundId, String gatewayRefundId, UUID actorId) {

		Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new RuntimeException("Refund not found"));

		if (refund.getStatus() == RefundStatus.SUCCESS) {
			throw new RuntimeException("Refund already completed");
		}

		if (refund.getStatus() != RefundStatus.INITIATED && refund.getStatus() != RefundStatus.PROCESSING) {
			throw new RuntimeException("Invalid refund state for completion");
		}

		refund.setStatus(RefundStatus.SUCCESS);
		refund.setGatewayRefundId(gatewayRefundId);
		refund.setCompletedAt(LocalDateTime.now());

		refundRepository.save(refund);

		Payment payment = paymentRepository.findById(refund.getPaymentId())
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (payment.getRefundedAmount() == null) {
			payment.setRefundedAmount(refund.getAmount());
		}

		paymentRepository.save(payment);
	}

	@Transactional
	@Override
	public void failRefund(UUID refundId, String reason, UUID actorId) {

		Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new RuntimeException("Refund not found"));

		if (refund.getStatus() == RefundStatus.SUCCESS) {
			throw new RuntimeException("Cannot fail a completed refund");
		}

		if (refund.getStatus() == RefundStatus.FAILED) {
			throw new RuntimeException("Refund already marked as failed");
		}

		refund.setStatus(RefundStatus.FAILED);
		refund.setFailureReason(reason);
		refund.setUpdatedAt(LocalDateTime.now());

		refundRepository.save(refund);

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

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (payment.getStatus() != PaymentStatus.SUCCESS) {
			throw new RuntimeException("Invoice can be generated only after successful payment");
		}

		invoiceRepository.findByOrderId(orderId).ifPresent(inv -> {
			throw new RuntimeException("Invoice already exists for this order");
		});

		String invoiceNumber = "INV-" + System.currentTimeMillis();
		String pdfUrl = "/invoices/" + invoiceNumber + ".pdf";

		Invoice invoice = Invoice.builder().orderId(orderId).paymentId(paymentId).invoiceNumber(invoiceNumber)
				.pdfUrl(pdfUrl).status(InvoiceStatus.GENERATED).retryCount(0).generatedAt(LocalDateTime.now())
				.createdBy(actorId).build();

		invoiceRepository.save(invoice);

		return InvoiceResponse.builder().invoiceId(invoice.getId()).invoiceNumber(invoice.getInvoiceNumber())
				.orderId(orderId).paymentId(paymentId).pdfUrl(pdfUrl).status(invoice.getStatus().name())
				.retryCount(invoice.getRetryCount()).generatedAt(invoice.getGeneratedAt()).build();
	}

	@Transactional
	@Override
	public InvoiceResponse regenerateInvoice(UUID invoiceId, UUID actorId) {

		Invoice invoice = invoiceRepository.findById(invoiceId)
				.orElseThrow(() -> new RuntimeException("Invoice not found"));

		int currentRetry = invoice.getRetryCount() != null ? invoice.getRetryCount() : 0;
		invoice.setRetryCount(currentRetry + 1);
		invoice.setStatus(InvoiceStatus.REGENERATED);

		String invoiceNumber = invoice.getInvoiceNumber();
		String pdfUrl = "/invoices/" + invoiceNumber + "_v" + invoice.getRetryCount() + ".pdf";

		invoice.setPdfUrl(pdfUrl);
		invoice.setUpdatedBy(actorId);
		invoice.setUpdatedAt(LocalDateTime.now());

		invoiceRepository.save(invoice);

		return InvoiceResponse.builder().invoiceId(invoice.getId()).invoiceNumber(invoice.getInvoiceNumber())
				.orderId(invoice.getOrderId()).paymentId(invoice.getPaymentId()).pdfUrl(invoice.getPdfUrl())
				.status(invoice.getStatus().name()).retryCount(invoice.getRetryCount())
				.generatedAt(invoice.getGeneratedAt()).build();
	}

	@Override
	public InvoiceResponse getInvoice(UUID invoiceId, UUID userId) {

		Invoice invoice = invoiceRepository.findById(invoiceId)
				.orElseThrow(() -> new RuntimeException("Invoice not found"));

		Order order = orderRepository.findById(invoice.getOrderId())
				.orElseThrow(() -> new RuntimeException("Order not found"));

		if (!order.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this invoice");
		}

		return InvoiceResponse.builder().invoiceId(invoice.getId()).invoiceNumber(invoice.getInvoiceNumber())
				.orderId(invoice.getOrderId()).paymentId(invoice.getPaymentId()).pdfUrl(invoice.getPdfUrl())
				.status(invoice.getStatus().name()).retryCount(invoice.getRetryCount())
				.generatedAt(invoice.getGeneratedAt()).sentAt(invoice.getSentAt()).build();
	}

	@Transactional
	@Override
	public void softDeletePayment(UUID paymentId, UUID actorId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new RuntimeException("Payment not found"));

		if (Boolean.TRUE.equals(payment.getIsDeleted())) {
			throw new RuntimeException("Payment already deleted");
		}

		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			throw new RuntimeException("Cannot delete a successful payment");
		}

		payment.setIsDeleted(true);
		payment.setDeletedAt(LocalDateTime.now());
		payment.setUpdatedBy(actorId);

		paymentRepository.save(payment);
	}

	private boolean isRazorpayMethod(PaymentMethod method) {
		return method == PaymentMethod.UPI || method == PaymentMethod.CARD || method == PaymentMethod.NETBANKING
				|| method == PaymentMethod.WALLET;
	}

	private long convertToPaise(Double amount) {
		return Math.round(amount * 100);
	}

	private PaymentResponse mapPaymentResponse(Payment payment, String message) {
		return PaymentResponse.builder().paymentId(payment.getId()).paymentReference(payment.getPaymentReference())
				.status(payment.getStatus().name()).message(message).amount(payment.getAmount())
				.method(payment.getMethod() != null ? payment.getMethod().name() : null)
				.gatewayName(payment.getGatewayName()).gatewayOrderId(payment.getGatewayOrderId())
				.gatewayPaymentId(payment.getGatewayPaymentId()).gatewaySignature(payment.getGatewaySignature())
				.gatewayTxnId(payment.getGatewayTxnId()).build();
	}

	@Transactional
	@Override
	public void handleRazorpayWebhook(String payload, String signature) {

		if (signature == null || signature.isBlank()) {
			throw new RuntimeException("Missing X-Razorpay-Signature header");
		}

		if (!isValidWebhookSignature(payload, signature, razorpayConfig.getWebhookSecret())) {
			throw new RuntimeException("Invalid Razorpay webhook signature");
		}

		try {
			JSONObject json = new JSONObject(payload);

			String eventId = json.optString("contains", null); // fallback placeholder
			String eventType = json.optString("event", null);
			String accountId = json.optString("account_id", null);

			// Some payloads may not expose a stable event id in the same place, so keep it
			// nullable.
			if (json.has("payload") && json.optJSONObject("payload") != null) {
				JSONObject payloadObj = json.optJSONObject("payload");

				JSONObject paymentEntity = null;
				JSONObject orderEntity = null;

				if (payloadObj.has("payment") && payloadObj.optJSONObject("payment") != null) {
					paymentEntity = payloadObj.optJSONObject("payment").optJSONObject("entity");
				}

				if (payloadObj.has("order") && payloadObj.optJSONObject("order") != null) {
					orderEntity = payloadObj.optJSONObject("order").optJSONObject("entity");
				}

				String gatewayPaymentId = paymentEntity != null ? paymentEntity.optString("id", null) : null;
				String gatewayOrderId = paymentEntity != null ? paymentEntity.optString("order_id", null) : null;

				if ((gatewayOrderId == null || gatewayOrderId.isBlank()) && orderEntity != null) {
					gatewayOrderId = orderEntity.optString("id", null);
				}

				String status = paymentEntity != null ? paymentEntity.optString("status", null)
						: orderEntity != null ? orderEntity.optString("status", null) : null;

				RazorpayWebhookLog log = RazorpayWebhookLog.builder().eventId(eventId).eventType(eventType)
						.accountId(accountId).signature(signature).gatewayOrderId(gatewayOrderId)
						.gatewayPaymentId(gatewayPaymentId).status(status).payload(payload).processed(false)
						.receivedAt(LocalDateTime.now()).build();

				RazorpayWebhookLog savedLog = razorpayWebhookLogRepository.save(log);

				try {
					if (gatewayOrderId != null && !gatewayOrderId.isBlank()) {
						updatePaymentFromWebhook(gatewayOrderId, gatewayPaymentId, eventType, payload);
					}

					savedLog.setProcessed(true);
					savedLog.setProcessingMessage("Webhook processed successfully");
					razorpayWebhookLogRepository.save(savedLog);

				} catch (Exception ex) {
					savedLog.setProcessed(false);
					savedLog.setProcessingMessage("Webhook received but payment update failed: " + ex.getMessage());
					razorpayWebhookLogRepository.save(savedLog);
					throw ex;
				}
			}

		} catch (Exception e) {
			throw new RuntimeException("Failed to process Razorpay webhook: " + e.getMessage(), e);
		}
	}

	@Transactional
	public void updatePaymentFromWebhook(String gatewayOrderId, String gatewayPaymentId, String eventType,
			String payload) {

		Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId)
				.orElseThrow(() -> new RuntimeException("Payment not found for gateway order id: " + gatewayOrderId));

		payment.setGatewayResponse(payload);
		payment.setUpdatedAt(LocalDateTime.now());

		if (gatewayPaymentId != null && !gatewayPaymentId.isBlank()) {
			payment.setGatewayPaymentId(gatewayPaymentId);
			payment.setGatewayTxnId(gatewayPaymentId);
		}

		switch (eventType) {
		case "payment.captured":
		case "order.paid":
			if (payment.getStatus() != PaymentStatus.SUCCESS) { // avoid duplicate processing
				payment.setStatus(PaymentStatus.SUCCESS);
				if (payment.getCompletedAt() == null) {
					payment.setCompletedAt(LocalDateTime.now());
				}
				paymentRepository.save(payment); // ✅ save payment first

				// ✅ Then confirm order + notify delivery
				orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
					if (order.getStatus() == com.karim.enums.OrderStatus.PENDING) {
						order.setStatus(com.karim.enums.OrderStatus.CONFIRMED);
						order.setConfirmedAt(LocalDateTime.now());
						orderRepository.save(order);
						orderNotificationService.notifyNewOrder(order); // ✅ NOTIFY HERE
					}
				});
			}
			break;

		case "payment.failed":
			if (payment.getStatus() != PaymentStatus.SUCCESS) {
				payment.setStatus(PaymentStatus.FAILED);
			}
			break;

		case "payment.authorized":
			if (payment.getStatus() != PaymentStatus.SUCCESS) {
				payment.setStatus(PaymentStatus.PENDING);
			}
			break;

		default:
			break;
		}

		paymentRepository.save(payment); // still save for non-captured events

	}

	@Override
	public RazorpayWebhookLogResponse getWebhookLogById(UUID webhookLogId) {
		RazorpayWebhookLog log = razorpayWebhookLogRepository.findById(webhookLogId)
				.orElseThrow(() -> new RuntimeException("Webhook log not found"));

		return mapWebhookLog(log);
	}

	@Override
	public List<RazorpayWebhookLogResponse> getAllWebhookLogs() {
		List<RazorpayWebhookLog> logs = razorpayWebhookLogRepository.findAll();
		List<RazorpayWebhookLogResponse> responses = new ArrayList<>();

		for (RazorpayWebhookLog log : logs) {
			responses.add(mapWebhookLog(log));
		}
		return responses;
	}

	private RazorpayWebhookLogResponse mapWebhookLog(RazorpayWebhookLog log) {
		return RazorpayWebhookLogResponse.builder().id(log.getId()).eventId(log.getEventId())
				.eventType(log.getEventType()).accountId(log.getAccountId()).gatewayOrderId(log.getGatewayOrderId())
				.gatewayPaymentId(log.getGatewayPaymentId()).status(log.getStatus()).processed(log.getProcessed())
				.processingMessage(log.getProcessingMessage()).payload(log.getPayload()).receivedAt(log.getReceivedAt())
				.build();
	}

	private boolean isValidWebhookSignature(String payload, String receivedSignature, String webhookSecret) {
		try {
			String expectedSignature = hmacSha256Hex(payload, webhookSecret);
			return expectedSignature.equals(receivedSignature);
		} catch (Exception e) {
			throw new RuntimeException("Webhook signature verification failed", e);
		}
	}

	private String hmacSha256Hex(String payload, String secret) throws Exception {
		Mac sha256Hmac = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		sha256Hmac.init(secretKey);

		byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

		StringBuilder hexString = new StringBuilder();
		for (byte b : hash) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}
}