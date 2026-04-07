package com.karim.service;

import java.util.UUID;

import com.karim.enums.EmailType;
import com.karim.enums.OtpPurpose;

public interface NotificationService {
	void sendEmail(UUID userId, EmailType emailType, String toEmail, String subject, String body, String referenceId);

	void markEmailSent(UUID emailLogId, String providerMessageId);

	void markEmailFailed(UUID emailLogId, String errorMessage);

	void retryFailedEmails();

	void markNotificationRead(UUID notificationId, UUID userId);

	void markAllNotificationsRead(UUID userId, UUID actorId);

	long getUnreadCount(UUID userId);

	void softDeleteNotification(UUID notificationId, UUID actorId);

	void dispatchOtp(UUID userId, OtpPurpose purpose, String referenceId);

	void dispatchOtp(UUID userId, OtpPurpose purpose, String referenceId, String toEmail);

	void sendDeliveryAssignedEmail(String toEmail, String customerName, Long orderId, Double totalAmount,
			String paymentType, String deliveryAddress, String agentId, String agentName, String agentMobile,
			String otp);
}
