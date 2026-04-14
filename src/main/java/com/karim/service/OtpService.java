package com.karim.service;

import java.util.UUID;

/**
 * Service for generating, validating, and invalidating delivery OTPs.
 * Implement this interface (e.g. via SMS/email) and register it as a Spring bean.
 */
public interface OtpService {

    /**
     * Generates a one-time password and sends it to the customer associated
     * with the given order (via SMS or email).
     *
     * @param orderId the order for which the OTP is generated
     */
    void generateAndSendOtp(UUID orderId);

    /**
     * Validates the OTP submitted by the delivery agent against the stored value.
     *
     * @param orderId the order being delivered
     * @param otp     the OTP entered by the agent
     * @return true if the OTP is correct and not expired; false otherwise
     */
    boolean validateOtp(UUID orderId, String otp);

    /**
     * Invalidates (deletes/expires) the OTP after a successful delivery so it
     * cannot be reused.
     *
     * @param orderId the order whose OTP should be invalidated
     */
    void invalidateOtp(UUID orderId);
}