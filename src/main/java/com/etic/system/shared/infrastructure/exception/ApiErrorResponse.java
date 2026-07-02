package com.etic.system.shared.infrastructure.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
