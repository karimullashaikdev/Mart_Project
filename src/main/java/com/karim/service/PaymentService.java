package com.karim.service;

import java.util.List;
import java.util.UUID;

import com.karim.dto.InvoiceResponse;
import com.karim.dto.PaymentResponse;
import com.karim.dto.RazorpayWebhookLogResponse;
import com.karim.enums.PaymentMethod;

public interface PaymentService {

	PaymentResponse initiatePayment(UUID orderId, UUID userId, PaymentMethod method, UUID actorId);

	PaymentResponse sendPaymentOtp(UUID paymentId, UUID userId);

	PaymentResponse verifyPaymentOtp(UUID paymentId, UUID userId, String otp);

	PaymentResponse verifyRazorpayPayment(UUID paymentId, UUID userId, String razorpayOrderId, String razorpayPaymentId,
			String razorpaySignature, UUID actorId);

	void handleRazorpayWebhook(String payload, String signature);

	RazorpayWebhookLogResponse getWebhookLogById(UUID webhookLogId);

	List<RazorpayWebhookLogResponse> getAllWebhookLogs();

	void confirmPaymentSuccess(UUID paymentId, String gatewayTxnId, String response, UUID actorId);

	void markPaymentFailed(UUID paymentId, String reason, UUID actorId);

	PaymentResponse getPayment(UUID paymentId, UUID userId);

	PaymentResponse getPaymentByOrder(UUID orderId);

	void initiateRefund(UUID paymentId, float amount, UUID returnRequestId, UUID actorId);

	void completeRefund(UUID refundId, String gatewayRefundId, UUID actorId);

	void failRefund(UUID refundId, String reason, UUID actorId);

	InvoiceResponse generateInvoice(UUID orderId, UUID paymentId, UUID actorId);

	InvoiceResponse regenerateInvoice(UUID invoiceId, UUID actorId);

	InvoiceResponse getInvoice(UUID invoiceId, UUID userId);

	void softDeletePayment(UUID paymentId, UUID actorId);
}