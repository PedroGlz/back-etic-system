package com.etic.system.shared.infrastructure.exception;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
	Instant timestamp,
	int status,
	String error,
	String message,
	String detail,
	String path,
	Map<String, String> validationErrors
) {
}
