package com.karim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplyCouponRequest {

    @NotBlank(message = "couponCode is required")
    @Size(max = 50, message = "couponCode too long")
    private String couponCode;
}