package com.etic.system.legacyimport.parser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class LegacyJsonContract {

	public static final int FORMAT_VERSION = 1;
	public static final Set<String> REQUIRED_DATASETS = Set.of(
		"customers", "customerSites", "equipment", "equipmentGroups", "equipmentFaultLinks", "manufacturers",
		"inspectionTypes", "inspectionStatuses", "inspectionDetailStatuses", "inspections",
		"inspectionDetails", "locations", "locationBaselines", "locationBaselinePhotos",
		"problems", "problemInspections", "pieProblemInspections", "problemPhotos",
		"problemSeverity", "priorityStatus", "faults", "faultTypes", "rootCauses",
		"piePhases", "pieEnvironments"
	);

	public static final Map<String, String> ID_FIELDS = idFields();

	private LegacyJsonContract() {
	}

	private static Map<String, String> idFields() {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("customers", "CustomerID");
		fields.put("customerSites", "CustomerSiteID");
		fields.put("equipment", "EquipmentID");
		fields.put("equipmentGroups", "EquipmentGroupID");
		fields.put("equipmentFaultLinks", "FaultLinkID");
		fields.put("manufacturers", "ManufacturerID");
		fields.put("inspectionTypes", "InspectionTypeID");
		fields.put("inspectionStatuses", "InspectionStatusID");
		fields.put("inspectionDetailStatuses", "InspectionDetailStatusID");
		fields.put("inspections", "InspectionID");
		fields.put("inspectionDetails", "InspectionDetailID");
		fields.put("locations", "LocationID");
		fields.put("locationBaselines", "BaselineID");
		fields.put("locationBaselinePhotos", "BaselinePhotoID");
		fields.put("problems", "ProblemID");
		fields.put("problemInspections", "ProblemInspectionID");
		fields.put("pieProblemInspections", "PIEProblemInspectionID");
		fields.put("problemPhotos", "ProblemPhotoID");
		fields.put("problemSeverity", "ProblemSeverityID");
		fields.put("priorityStatus", "PriorityStatusID");
		fields.put("faults", "FaultID");
		fields.put("faultTypes", "FaultTypeID");
		fields.put("rootCauses", "RootCauseID");
		fields.put("piePhases", "PIEPhaseID");
		fields.put("pieEnvironments", "PIEEnvironmentID");
		return Map.copyOf(fields);
	}
}
