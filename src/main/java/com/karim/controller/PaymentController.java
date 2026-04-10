package com.karim.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karim.annotation.CurrentUser;
import com.karim.dto.ApiResponse;
import com.karim.dto.InitiatePaymentRequest;
import com.karim.dto.PaymentResponse;
import com.karim.dto.RazorpayWebhookLogResponse;
import com.karim.dto.VerifyOtpRequest;
import com.karim.dto.VerifyRazorpayPaymentRequest;
import com.karim.dto.WebhookAckResponse;
import com.karim.service.PaymentService;
import com.karim.service.impl.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	@PostMapping("/initiate")
	public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(@CurrentUser UserPrincipal principal,
			@Valid @RequestBody InitiatePaymentRequest request) {

		PaymentResponse response = paymentService.initiatePayment(request.getOrderId(), principal.getId(),
				request.getMethod(), principal.getId());

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@PostMapping("/{paymentId}/send-otp")
	public ResponseEntity<ApiResponse<PaymentResponse>> sendPaymentOtp(@PathVariable UUID paymentId,
			@CurrentUser UserPrincipal principal) {

		PaymentResponse response = paymentService.sendPaymentOtp(paymentId, principal.getId());

		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/{paymentId}/verify-otp")
	public ResponseEntity<ApiResponse<PaymentResponse>> verifyPaymentOtp(@PathVariable UUID paymentId,
			@CurrentUser UserPrincipal principal, @Valid @RequestBody VerifyOtpRequest request) {

		PaymentResponse response = paymentService.verifyPaymentOtp(paymentId, principal.getId(), request.getOtp());

		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/verify-razorpay")
	public ResponseEntity<ApiResponse<PaymentResponse>> verifyRazorpayPayment(@CurrentUser UserPrincipal principal,
			@Valid @RequestBody VerifyRazorpayPaymentRequest request) {

		PaymentResponse response = paymentService.verifyRazorpayPayment(request.getPaymentId(), principal.getId(),
				request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature(),
				principal.getId());

		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@GetMapping("/{paymentId}")
	public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID paymentId,
			@CurrentUser UserPrincipal principal) {

		PaymentResponse response = paymentService.getPayment(paymentId, principal.getId());

		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@GetMapping("/order/{orderId}")
	public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(@PathVariable UUID orderId) {

		PaymentResponse response = paymentService.getPaymentByOrder(orderId);

		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<WebhookAckResponse> handleRazorpayWebhook(
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
			@RequestBody String payload) {

		paymentService.handleRazorpayWebhook(payload, signature);
		return ResponseEntity.ok(new WebhookAckResponse("Webhook processed successfully"));
	}

	@GetMapping("/webhook-logs")
	public ResponseEntity<ApiResponse<List<RazorpayWebhookLogResponse>>> getAllWebhookLogs() {
		return ResponseEntity.ok(ApiResponse.success(paymentService.getAllWebhookLogs()));
	}

	@GetMapping("/webhook-logs/{webhookLogId}")
	public ResponseEntity<ApiResponse<RazorpayWebhookLogResponse>> getWebhookLogById(@PathVariable UUID webhookLogId) {
		return ResponseEntity.ok(ApiResponse.success(paymentService.getWebhookLogById(webhookLogId)));
	}
}