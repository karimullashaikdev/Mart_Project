package com.karim.exception;

public class CartEmptyException extends RuntimeException {
    public CartEmptyException() {
        super("Cannot proceed: the cart is empty.");
    }
}