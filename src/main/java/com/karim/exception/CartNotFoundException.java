package com.karim.exception;

import java.util.UUID;

public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(UUID userId) {
        super("No active cart found for user: " + userId);
    }
}