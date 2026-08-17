package com.etic.system.legacyimport.model;

import java.time.LocalDateTime;

public record LegacyImportJob(
	String id,
	String filename,
	LegacyImportStatus status,
	String phase,
	int progress,
	LocalDateTime startedAt,
	LocalDateTime finishedAt,
	String errorMessage,
	String createdBy
) {
}
