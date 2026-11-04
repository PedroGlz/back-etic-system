package com.etic.system.legacyimport.model;

import java.util.List;
import java.util.Map;

public final class LegacyHomologation {
	private LegacyHomologation() {}

	public record Option(String id, String label, String parentId, String clientId, boolean defaultValue) {}
	public record InspectionOption(String inspectionId, Long inspectionNumber, String inspectionDate, String customerName) {}
	public record Priority(String sourceId, String sourceLabel, boolean missing) {}
	public record PriorityImpact(
		String locationId,
		String locationName,
		String sourcePriorityId,
		String sourcePriorityName,
		List<String> jobInspectionIds,
		List<String> jobInspectionNames,
		List<String> externalInspectionIds,
		List<String> externalInspectionNames
	) {}
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
		List<PriorityImpact> priorityImpacts,
		List<Option> customers,
		List<Option> priorities,
		List<Option> siteGroups,
		List<Option> sites
	) {}
	public record Request(
		String inspectionId,
		String customerId,
		String legalName,
		String siteGroupId,
		String siteGroupName,
		String siteId,
		String siteName,
		boolean automaticSelection,
		Map<String, String> priorityMappings,
		boolean forceGlobalPriorityUpdate,
		boolean confirmRelationshipChanges
	) {}
	public record Selection(
		String inspectionId,
		Long inspectionNumber,
		boolean automaticSelection,
		String sourceCustomerId,
		String customerId,
		String sourceLegalName,
		String sourceSiteId,
		String legalName,
		boolean updateLegalName,
		String siteGroupId,
		String siteGroupName,
		boolean createSiteGroup,
		boolean updateSiteGroupName,
		boolean updateSiteGroupCustomer,
		String siteId,
		String siteName,
		boolean updateSite,
		boolean moveLocationsToSite,
		boolean relationshipChangesConfirmed,
		List<String> relationshipInspectionIds,
		Map<String, String> priorityMappings,
		boolean forceGlobalPriorityUpdate,
		List<PriorityImpact> priorityImpacts,
		String confirmedBy
	) {}
}
