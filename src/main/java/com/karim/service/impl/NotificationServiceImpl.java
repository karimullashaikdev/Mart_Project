package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.karim.entity.EmailLog;
import com.karim.entity.Notification;
import com.karim.entity.Otp;
import com.karim.enums.EmailStatus;
import com.karim.enums.EmailType;
import com.karim.enums.OtpPurpose;
import com.karim.repository.EmailLogRepository;
import com.karim.repository.NotificationRepository;
import com.karim.repository.OtpRepository;
import com.karim.service.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService {

	private final EmailLogRepository emailLogRepository;
	private final NotificationRepository notificationRepository;
	private final OtpRepository otpRepository;

	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${brevo.api.key}")
	private String apiKey;

	@Value("${brevo.sender.email}")
	private String senderEmail;

	@Value("${brevo.sender.name}")
	private String senderName;

	@Value("${app.base-url}")
	private String baseUrl;

	private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

	public NotificationServiceImpl(EmailLogRepository emailLogRepository, NotificationRepository notificationRepository,
			OtpRepository otpRepository) {
		this.emailLogRepository = emailLogRepository;
		this.notificationRepository = notificationRepository;
		this.otpRepository = otpRepository;
	}

	// -------------------------
	// COMMON METHOD TO SEND EMAIL VIA BREVO
	// -------------------------
	private void sendBrevoEmail(UUID userId, EmailType emailType, String toEmail, String subject, String htmlContent,
			byte[] attachment, String referenceId) {

		EmailLog emailLog = new EmailLog();
		emailLog.setUserId(userId);
		emailLog.setReferenceId(referenceId);
		emailLog.setEmailType(emailType);
		emailLog.setToEmail(toEmail);
		emailLog.setSubject(subject);
		emailLogRepository.save(emailLog);

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("api-key", apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);

			String safeHtml = htmlContent.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");

			StringBuilder json = new StringBuilder();
			json.append("{");
			json.append("\"sender\":{\"name\":\"").append(senderName).append("\",\"email\":\"").append(senderEmail)
					.append("\"},");
			json.append("\"to\":[{\"email\":\"").append(toEmail).append("\"}],");
			json.append("\"subject\":\"").append(subject).append("\",");
			json.append("\"htmlContent\":\"").append(safeHtml).append("\"");

			if (attachment != null) {
				String base64 = Base64.getEncoder().encodeToString(attachment);
				json.append(",\"attachment\":[{\"content\":\"").append(base64).append("\",\"name\":\"Invoice.pdf\"}]");
			}

			json.append("}");

			HttpEntity<String> request = new HttpEntity<>(json.toString(), headers);
			ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

			emailLog.setStatus(EmailStatus.SENT);
			emailLog.setSentAt(LocalDateTime.now());
			emailLog.setProviderMessageId("brevo-response-" + response.getStatusCodeValue());

		} catch (Exception ex) {
			emailLog.setStatus(EmailStatus.FAILED);
			emailLog.setErrorMessage(ex.getMessage());
			emailLog.setFailedAt(LocalDateTime.now());
		} finally {
			emailLogRepository.save(emailLog);
		}
	}

	// -------------------------
	// SEND OTP
	// -------------------------
	@Transactional
	@Override
	public void dispatchOtp(UUID userId, OtpPurpose purpose, String referenceId, String toEmail) {
		String otp = String.format("%06d", (int) (Math.random() * 1_000_000));
		String otpHash = Integer.toString(otp.hashCode());

		Otp otpEntity = new Otp();
		otpEntity.setUserId(userId);
		otpEntity.setPurpose(purpose);
		otpEntity.setReferenceId(referenceId);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpRepository.save(otpEntity);

		String subject = "Your OTP Code";
		String body = "Dear Customer,<br><br>Your OTP is: <b>" + otp
				+ "</b> (valid for 5 minutes)<br><br>Karim Mart Team";
		sendBrevoEmail(userId, EmailType.OTP, toEmail, subject, body, null, referenceId);
	}

	// -------------------------
	// MARK EMAIL FAILED / SENT / RETRY
	// -------------------------
	@Transactional
	@Override
	public void markEmailSent(UUID emailLogId, String providerMessageId) {
		EmailLog emailLog = emailLogRepository.findById(emailLogId)
				.orElseThrow(() -> new RuntimeException("Email log not found"));
		if (emailLog.getStatus() == EmailStatus.SENT)
			throw new RuntimeException("Email already sent");

		emailLog.setStatus(EmailStatus.SENT);
		emailLog.setSentAt(LocalDateTime.now());
		emailLog.setProviderMessageId(providerMessageId);
		emailLogRepository.save(emailLog);
	}

	@Transactional
	@Override
	public void markEmailFailed(UUID emailLogId, String errorMessage) {
		EmailLog emailLog = emailLogRepository.findById(emailLogId)
				.orElseThrow(() -> new RuntimeException("Email log not found"));
		if (emailLog.getStatus() == EmailStatus.SENT)
			throw new RuntimeException("Cannot mark a sent email as failed");

		emailLog.setRetryCount(emailLog.getRetryCount() + 1);
		emailLog.setStatus(EmailStatus.FAILED);
		emailLog.setErrorMessage(errorMessage);
		emailLog.setFailedAt(LocalDateTime.now());
		emailLogRepository.save(emailLog);
	}

	@Transactional
	@Override
	public void retryFailedEmails() {
		final int MAX_RETRY = 3;
		List<EmailLog> failed = emailLogRepository.findByStatusAndRetryCountLessThan(EmailStatus.FAILED, MAX_RETRY);
		for (EmailLog e : failed) {
			sendBrevoEmail(e.getUserId(), e.getEmailType(), e.getToEmail(), e.getSubject(), "Retry email content", null,
					e.getReferenceId());
		}
	}

	// -------------------------
	// NOTIFICATIONS
	// -------------------------
	@Transactional
	@Override
	public void markNotificationRead(UUID notificationId, UUID userId) {
		Notification n = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new RuntimeException("Notification not found"));
		if (!n.getUserId().equals(userId))
			throw new RuntimeException("Unauthorized");
		if (!n.isRead()) {
			n.setRead(true);
			n.setReadAt(LocalDateTime.now());
			notificationRepository.save(n);
		}
	}

	@Transactional
	@Override
	public void markAllNotificationsRead(UUID userId, UUID actorId) {
		List<Notification> list = notificationRepository.findByUserIdAndIsReadFalse(userId);
		LocalDateTime now = LocalDateTime.now();
		list.forEach(n -> {
			n.setRead(true);
			n.setReadAt(now);
		});
		notificationRepository.saveAll(list);
	}

	@Override
	public long getUnreadCount(UUID userId) {
		return notificationRepository.countByUserIdAndIsReadFalse(userId);
	}

	@Transactional
	@Override
	public void softDeleteNotification(UUID notificationId, UUID actorId) {
		Notification n = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new RuntimeException("Notification not found"));
		if (n.isDeleted())
			throw new RuntimeException("Notification already deleted");
		n.setDeleted(true);
		n.setDeletedAt(LocalDateTime.now());
		n.setDeletedBy(actorId);
		notificationRepository.save(n);
	}

	// -------------------------
	// DELIVERY ASSIGNED EMAIL
	// -------------------------
	@Override
	@Async
	public void sendDeliveryAssignedEmail(String toEmail, String customerName, Long orderId, Double totalAmount,
			String paymentType, String deliveryAddress, String agentId, String agentName, String agentMobile,
			String otp) {

		String subject = "Your Order #" + orderId + " is On the Way! 🛵";
		String trackingLink = baseUrl + "/tracking.html?orderId=" + orderId;

		String html = "<p>Hi <b>" + customerName + "</b>, your order has been assigned!</p>"
				+ "<p>OTP for delivery: <b>" + otp + "</b></p>" + "<p><a href='" + trackingLink
				+ "'>Track Order</a></p>";

		sendBrevoEmail(null, EmailType.DELIVERY_ASSIGNED, toEmail, subject, html, null, null);
	}

	@Override
	@Async
	public void sendEmail(UUID userId, EmailType emailType, String toEmail, String subject, String body,
			String referenceId) {

		EmailLog emailLog = new EmailLog();
		emailLog.setUserId(userId);
		emailLog.setReferenceId(referenceId);
		emailLog.setEmailType(emailType);
		emailLog.setToEmail(toEmail);
		emailLog.setSubject(subject);
		emailLogRepository.save(emailLog);

		try {
			// Prepare Brevo payload
			HttpHeaders headers = new HttpHeaders();
			headers.set("api-key", apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);

			String safeHtml = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");

			String json = "{" + "\"sender\":{\"name\":\"" + senderName + "\",\"email\":\"" + senderEmail + "\"},"
					+ "\"to\":[{\"email\":\"" + toEmail + "\"}]," + "\"subject\":\"" + subject + "\","
					+ "\"htmlContent\":\"" + safeHtml + "\"" + "}";

			HttpEntity<String> request = new HttpEntity<>(json, headers);
			ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

			emailLog.setStatus(EmailStatus.SENT);
			emailLog.setSentAt(LocalDateTime.now());
			emailLog.setProviderMessageId("brevo-" + response.getStatusCodeValue());

		} catch (Exception ex) {
			emailLog.setStatus(EmailStatus.FAILED);
			emailLog.setErrorMessage(ex.getMessage());
			emailLog.setFailedAt(LocalDateTime.now());
		} finally {
			emailLogRepository.save(emailLog);
		}
	}

	@Override
	public void dispatchOtp(UUID userId, OtpPurpose purpose, String referenceId) {
		// Generate OTP
		String otp = String.format("%06d", (int) (Math.random() * 1_000_000));
		String otpHash = Integer.toString(otp.hashCode());

		// Save OTP entity
		Otp otpEntity = new Otp();
		otpEntity.setUserId(userId);
		otpEntity.setPurpose(purpose);
		otpEntity.setReferenceId(referenceId);
		otpEntity.setOtpHash(otpHash);
		otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpRepository.save(otpEntity);

		// Send OTP email
		String subject = "Your OTP Code";
		String body = "Dear Customer,<br><br>Your OTP is: <b>" + otp
				+ "</b> (valid for 5 minutes)<br><br>Karim Mart Team";

		// You can reuse sendEmail method
		sendEmail(userId, EmailType.OTP, "user@example.com", subject, body, referenceId);
	}
}