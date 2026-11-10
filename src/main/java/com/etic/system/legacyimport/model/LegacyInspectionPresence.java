package com.etic.system.legacyimport.model;

public record LegacyInspectionPresence(
	String inspectionId,
	Long inspectionNumber,
	String customerName,
	String siteName,
	boolean existing
) {}
