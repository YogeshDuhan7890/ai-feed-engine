package com.yogesh.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard API Response — har endpoint same format return kare Frontend
 * reliably parse kar sake
 */
@Data
public class ApiResponse<T> {
	private boolean success;
	private String message;
	private T data;
	private String error;
	private LocalDateTime timestamp = LocalDateTime.now();

	// Pagination fields (optional)
	private String nextCursor;
	private Boolean hasMore;

	// ── Success responses ─────────────────────────────────────────
	public static <T> ApiResponse<T> ok(T data) {
		ApiResponse<T> r = new ApiResponse<>();
		r.success = true;
		r.data = data;
		return r;
	}

	public static <T> ApiResponse<T> ok(String message, T data) {
		ApiResponse<T> r = new ApiResponse<>();
		r.success = true;
		r.message = message;
		r.data = data;
		return r;
	}

	public static ApiResponse<Void> ok(String message) {
		ApiResponse<Void> r = new ApiResponse<>();
		r.success = true;
		r.message = message;
		return r;
	}

	// ── Error responses ───────────────────────────────────────────
	public static <T> ApiResponse<T> error(String errorMsg) {
		ApiResponse<T> r = new ApiResponse<>();
		r.success = false;
		r.error = errorMsg;
		return r;
	}

	public static <T> ApiResponse<T> fail(String errorMsg) {
		return error(errorMsg);
	}

	public static <T> ApiResponse<T> fail(String errorMsg, T data) {
		ApiResponse<T> r = new ApiResponse<>();
		r.success = false;
		r.error = errorMsg;
		r.data = data;
		return r;
	}

	// ── Paginated response ────────────────────────────────────────
	public static <T> ApiResponse<List<T>> paged(List<T> data, String nextCursor, boolean hasMore) {
		ApiResponse<List<T>> r = new ApiResponse<>();
		r.success = true;
		r.data = data;
		r.nextCursor = nextCursor;
		r.hasMore = hasMore;
		return r;
	}
}
