package com.etic.system.shared.infrastructure.exception;

import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.CatalogNotSupportedException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CatalogNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleCatalogNotSupported(
		CatalogNotSupportedException exception,
		HttpServletRequest request
	) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
		ResourceNotFoundException exception,
		HttpServletRequest request
	) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
	}

	@ExceptionHandler(BusinessValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleBusinessValidation(
		BusinessValidationException exception,
		HttpServletRequest request
	) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		Map<String, String> validationErrors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return build(HttpStatus.BAD_REQUEST, "La solicitud contiene errores de validación", request, validationErrors);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
		ResponseStatusException exception,
		HttpServletRequest request
	) {
		return build(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason(), request, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
		Exception exception,
		HttpServletRequest request
	) {
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno", request, null);
	}

	private ResponseEntity<ApiErrorResponse> build(
		HttpStatus status,
		String message,
		HttpServletRequest request,
		Map<String, String> validationErrors
	) {
		ApiErrorResponse body = new ApiErrorResponse(
			Instant.now(),
			status.value(),
			status.getReasonPhrase(),
			message,
			message,
			request.getRequestURI(),
			validationErrors
		);
		return ResponseEntity.status(status).body(body);
	}
}
