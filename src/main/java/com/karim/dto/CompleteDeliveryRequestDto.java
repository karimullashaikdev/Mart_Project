package com.karim.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /api/delivery/orders/{orderId}/complete
 * Agent submits the 4-digit OTP collected from the customer.
 */
@Getter
@Setter
public class CompleteDeliveryRequestDto {

    /** 4-digit OTP provided by the customer at the door */
    private String otp;
}