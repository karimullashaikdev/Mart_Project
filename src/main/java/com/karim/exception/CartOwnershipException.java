package com.karim.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CartOwnershipException extends RuntimeException {

	public CartOwnershipException() {
		super("You do not have permission to access this cart.");
	}
}
