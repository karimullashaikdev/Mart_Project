package com.karim.service.impl;


import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.karim.service.OtpService;

/**
 * In-memory OTP store.
 *
 * For production: replace the ConcurrentHashMap with Redis
 * so OTPs survive server restarts and work across multiple instances.
 *
 * Redis equivalent:
 *   redisTemplate.opsForValue().set("otp:" + orderId, otp, 30, TimeUnit.MINUTES);
 *   redisTemplate.opsForValue().get("otp:" + orderId);
 *   redisTemplate.delete("otp:" + orderId);
 */
@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    // OTP expiry duration in minutes
    private static final int OTP_EXPIRY_MINUTES = 30;

    private record OtpEntry(String otp, LocalDateTime expiresAt) {}

    // orderId → OtpEntry
    private final Map<UUID, OtpEntry> otpStore = new ConcurrentHashMap<>();

    private final SecureRandom random = new SecureRandom();

    @Override
    public void generateAndSendOtp(UUID orderId) {

        // Generate 4-digit OTP: 1000–9999
        String otp = String.format("%04d", 1000 + random.nextInt(9000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        otpStore.put(orderId, new OtpEntry(otp, expiresAt));

        // ── Send to customer ──────────────────────────────────────────────────
        // TODO: Replace the log line below with your SMS/email implementation.
        //
        // SMS example (Twilio):
        //   twilioClient.messages.create(
        //       new MessageCreator(customerPhone, fromNumber, "Your Karim delivery OTP: " + otp)
        //   );
        //
        // Email example (JavaMailSender):
        //   mailSender.send(buildOtpEmail(customerEmail, otp));
        //
        // For now, OTP is logged at INFO level so you can test it manually.
        log.info("[OTP] Order {} → OTP = {} (expires at {})", orderId, otp, expiresAt);
    }

    @Override
    public boolean validateOtp(UUID orderId, String submittedOtp) {

        OtpEntry entry = otpStore.get(orderId);

        if (entry == null) {
            log.warn("[OTP] No OTP found for order {}", orderId);
            return false;
        }

        if (LocalDateTime.now().isAfter(entry.expiresAt())) {
            log.warn("[OTP] OTP for order {} has expired", orderId);
            otpStore.remove(orderId);
            return false;
        }

        boolean valid = entry.otp().equals(submittedOtp != null ? submittedOtp.trim() : "");
        if (!valid) {
            log.warn("[OTP] Wrong OTP submitted for order {}", orderId);
        }
        return valid;
    }

    @Override
    public void invalidateOtp(UUID orderId) {
        otpStore.remove(orderId);
        log.debug("[OTP] OTP invalidated for order {}", orderId);
    }
}