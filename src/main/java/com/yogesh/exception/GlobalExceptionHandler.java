package com.yogesh.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@Slf4j
@ResponseBody
@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler({ ValidationException.class, MethodArgumentNotValidException.class })
	public ResponseEntity<ErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", validationMessage(ex), request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
		return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", request);
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest request) {
		log.error("RuntimeException on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Server error. Dobara try karo.", request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
		log.warn("Bad request on {}: {}", request.getRequestURI(), ex.getMessage());
		return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
		log.warn("Data integrity violation on {}: {}", request.getRequestURI(), message);
		return build(HttpStatus.CONFLICT, "DUPLICATE_ENTRY", "Data constraint violation", request);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex,
			HttpServletRequest request) {
		return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
				"File too large. Maximum allowed size exceed ho gayi", request);
	}

	@ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(jakarta.persistence.EntityNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Server error. Dobara try karo.", request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
			HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(code, message, LocalDateTime.now(), request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}

	private String validationMessage(Exception ex) {
		if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
			return methodArgumentNotValidException.getBindingResult()
					.getFieldErrors()
					.stream()
					.findFirst()
					.map(error -> error.getField() + ": " + error.getDefaultMessage())
					.orElse("Validation failed");
		}

		return ex.getMessage() != null ? ex.getMessage() : "Validation failed";
	}

	public record ErrorResponse(String code, String message, LocalDateTime timestamp, String path) {
	}
}
