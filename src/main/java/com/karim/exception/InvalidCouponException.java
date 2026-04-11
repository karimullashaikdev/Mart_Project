package com.karim.exception;

public class InvalidCouponException extends RuntimeException {
    public InvalidCouponException(String couponCode) {
        super("Coupon code '" + couponCode + "' is invalid or expired.");
    }
}