package com.etic.system.legacyimport.model;

import java.util.List;
import java.util.Map;

public final class LegacyHomologation {
	private LegacyHomologation() {}

	public record Option(String id, String label, String parentId) {}
	public record InspectionOption(String inspectionId, Long inspectionNumber, String inspectionDate, String customerName) {}
	public record Priority(String sourceId, String sourceLabel) {}
	public record Review(
		String inspectionId,
		Long inspectionNumber,
		String automaticInspectionId,
		List<InspectionOption> inspections,
		String customerId,
		String customerName,
		String siteId,
		String siteName,
		List<Priority> inactivePriorities,
		List<String> priorityBlockers,
		List<Option> customers,
		List<Option> priorities,
		List<Option> siteGroups,
		List<Option> sites
	) {}
	public record Request(
		String inspectionId,
		String legalName,
		String siteGroupId,
		String siteId,
		boolean automaticSelection,
		Map<String, String> priorityMappings
	) {}
	public record Selection(
		String inspectionId,
		Long inspectionNumber,
		boolean automaticSelection,
		String customerId,
		String sourceLegalName,
		String sourceSiteId,
		String legalName,
		boolean updateLegalName,
		String siteGroupId,
		String siteId,
		Map<String, String> priorityMappings
	) {}
}
