package com.karim.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CartItemAlreadyExistsException extends RuntimeException {

	public CartItemAlreadyExistsException(UUID productId) {
		super("Product " + productId + " is already in the cart. Use update endpoint to change quantity.");
	}
}