package com.karim.exception;

public class CartItemLimitExceededException extends RuntimeException {
    public CartItemLimitExceededException() {
        super("Cart cannot hold more than 50 distinct products.");
    }
}