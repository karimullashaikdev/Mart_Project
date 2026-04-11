package com.karim.exception;

import java.util.UUID;

public class ProductOutOfStockException extends RuntimeException {
    public ProductOutOfStockException(UUID productId) {
        super("Product " + productId + " is out of stock.");
    }

    public ProductOutOfStockException(UUID productId, int requested, int available) {
        super("Only " + available + " unit(s) available for product " + productId + ", but " + requested + " requested.");
    }
}