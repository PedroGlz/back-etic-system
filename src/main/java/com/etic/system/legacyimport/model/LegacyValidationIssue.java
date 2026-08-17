package com.etic.system.legacyimport.model;

public record LegacyValidationIssue(
	ValidationSeverity severity,
	String code,
	String dataset,
	String recordId,
	String message
) {
}
