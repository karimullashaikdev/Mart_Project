package com.karim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponse<T> {

	private boolean success;
	private String message;
	private T data;

	// ✅ SUCCESS RESPONSE (with data)
	public static <T> ApiResponse<T> success(T data) {
		return ApiResponse.<T>builder().success(true).message("Request successful").data(data).build();
	}

	// ✅ SUCCESS RESPONSE (with custom message)
	public static <T> ApiResponse<T> success(String message, T data) {
		return ApiResponse.<T>builder().success(true).message(message).data(data).build();
	}

	// ❌ FAILURE RESPONSE
	public static <T> ApiResponse<T> failure(String message) {
		return ApiResponse.<T>builder().success(false).message(message).data(null).build();
	}
}
