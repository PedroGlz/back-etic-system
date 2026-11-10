package com.etic.system.legacyimport.model;

import java.time.LocalDateTime;
import java.util.List;

public record LegacyImportError(
	String processId,
	String filename,
	String inspectionId,
	Long inspectionNumber,
	String stage,
	String status,
	String message,
	String rootCause,
	List<String> causes,
	String exceptionType,
	String technicalDetail,
	LocalDateTime occurredAt
) {}
