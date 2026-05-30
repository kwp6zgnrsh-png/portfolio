package com.study.backend.common.dto;

public record ApiResponse<T>(
	T payload,
	String message
) {
	public static <T> ApiResponse<T> of(String message, T payload) {
		return new ApiResponse<>(payload, message);
	}

	public static ApiResponse<Void> of(String message) {
		return new ApiResponse<>(null, message);
	}
}
