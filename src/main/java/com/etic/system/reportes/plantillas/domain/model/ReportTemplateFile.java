package com.etic.system.reportes.plantillas.domain.model;

public record ReportTemplateFile(
	String name,
	String extension,
	long size,
	boolean deletable,
	String downloadUrl
) {
}
