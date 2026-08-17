package com.etic.system.legacyimport.parser;

import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyValidationIssue;
import com.etic.system.legacyimport.model.ValidationSeverity;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LegacyImportAccumulator {

	private static final int MAX_REPORTED_ISSUES = 10_000;
	private static final Set<String> KNOWN_TEMPERATURE_UNITS = Set.of(
		"C", "CELSIUS", "CENTIGRADE", "°C", "F", "FAHRENHEIT", "°F"
	);

	private final long maxRecords;
	private final Map<String, Long> counts = new LinkedHashMap<>();
	private final Map<String, Set<String>> ids = new HashMap<>();
	private final List<LegacyValidationIssue> issues = new ArrayList<>();
	private final Map<String, String> locationParents = new HashMap<>();
	private final Map<String, String> problemPriors = new HashMap<>();
	private final List<Reference> references = new ArrayList<>();
	private long totalRecords;
	private long chronicProblems;

	LegacyImportAccumulator(long maxRecords) {
		this.maxRecords = maxRecords;
	}

	void accept(String dataset, JsonNode row) {
		totalRecords++;
		if (totalRecords > maxRecords) {
			throw new LegacyJsonFormatException("El JSON excede el límite configurable de " + maxRecords + " registros");
		}
		counts.merge(dataset, 1L, Long::sum);

		String idField = LegacyJsonContract.ID_FIELDS.get(dataset);
		String id = text(row, idField);
		if (idField != null) {
			if (id == null) {
				issue(ValidationSeverity.ERROR, "MISSING_ID", dataset, null, "Falta " + idField);
			} else if (!ids.computeIfAbsent(dataset, ignored -> new HashSet<>()).add(id)) {
				issue(ValidationSeverity.ERROR, "DUPLICATE_ID", dataset, id, "ID duplicado: " + id);
			}
		}

		switch (dataset) {
			case "customerSites" -> reference(dataset, id, "CustomerID", text(row, "CustomerID"), "customers");
			case "equipment" -> {
				reference(dataset, id, "EquipmentGroupID", text(row, "EquipmentGroupID"), "equipmentGroups");
				reference(dataset, id, "InspectionTypeID", text(row, "InspectionTypeID"), "inspectionTypes");
			}
			case "equipmentFaultLinks" -> {
				reference(dataset,id,"EquipmentID",text(row,"EquipmentID"),"equipment");
				reference(dataset,id,"FaultID",text(row,"FaultID"),"faults");
			}
			case "locations" -> acceptLocation(row, id);
			case "inspections" -> acceptInspection(row, id);
			case "inspectionDetails" -> {
				reference(dataset, id, "InspectionID", text(row, "InspectionID"), "inspections");
				reference(dataset, id, "LocationID", text(row, "LocationID"), "locations");
			}
			case "locationBaselines" -> {
				reference(dataset, id, "InspectionID", text(row, "InspectionID"), "inspections");
				reference(dataset, id, "LocationID", text(row, "LocationID"), "locations");
			}
			case "locationBaselinePhotos" -> reference(dataset, id, "BaselineID", text(row, "BaselineID"), "locationBaselines");
			case "problems" -> acceptProblem(row, id);
			case "problemInspections" -> {
				reference(dataset, id, "ProblemID", text(row, "ProblemID"), "problems");
				reference(dataset, id, "InspectionID", text(row, "InspectionID"), "inspections");
				reference(dataset, id, "InspectionDetailID", text(row, "InspectionDetailID"), "inspectionDetails");
			}
			case "pieProblemInspections" -> reference(
				dataset, id, "ProblemInspectionID", text(row, "ProblemInspectionID"), "problemInspections"
			);
			case "problemPhotos" -> reference(
				dataset, id, "ProblemInspectionID", text(row, "ProblemInspectionID"), "problemInspections"
			);
			default -> {
				// Los catálogos sin relaciones críticas se validan por ID y duplicados.
			}
		}
	}

	LegacyImportAnalysis finish(Set<String> seenDatasets, Integer formatVersion, boolean metadataPresent) {
		if (formatVersion == null) {
			issue(ValidationSeverity.ERROR, "MISSING_FORMAT_VERSION", null, null, "Falta formatVersion");
		} else if (formatVersion != LegacyJsonContract.FORMAT_VERSION) {
			issue(ValidationSeverity.ERROR, "UNSUPPORTED_FORMAT_VERSION", null, null,
				"formatVersion no soportado: " + formatVersion);
		}
		if (!metadataPresent) {
			issue(ValidationSeverity.ERROR, "MISSING_METADATA", null, null, "Falta metadata");
		}
		for (String required : LegacyJsonContract.REQUIRED_DATASETS) {
			if (!seenDatasets.contains(required)) {
				issue(ValidationSeverity.ERROR, "MISSING_DATASET", required, null, "Dataset obligatorio ausente");
			}
		}
		validateReferences();
		validateCycles(locationParents, "LOCATION_CYCLE", "locations");
		validateCycles(problemPriors, "CHRONIC_CYCLE", "problems");

		return result();
	}

	LegacyImportAnalysis finishSql() {
		validateReferences();
		validateCycles(locationParents, "LOCATION_CYCLE", "locations");
		validateCycles(problemPriors, "CHRONIC_CYCLE", "problems");
		return result();
	}

	private LegacyImportAnalysis result() {
		return new LegacyImportAnalysis(count("customers"),count("customerSites"),count("equipment"),count("equipmentGroups"),
			count("manufacturers"),count("inspectionTypes"),count("inspectionStatuses"),count("inspectionDetailStatuses"),
			count("inspections"),count("inspectionDetails"),count("locations"),count("locationBaselines"),count("locationBaselinePhotos"),
			count("problems"),chronicProblems,count("problemInspections"),count("pieProblemInspections"),count("problemPhotos"),
			count("problemSeverity"),count("priorityStatus"),count("faults"),count("faultTypes"),count("rootCauses"),
			count("piePhases"),count("pieEnvironments"),count("equipmentFaultLinks"),List.copyOf(issues));
	}

	private void acceptLocation(JsonNode row, String id) {
		reference("locations", id, "CustomerSiteID", text(row, "CustomerSiteID"), "customerSites");
		String parent = text(row, "ParentID");
		if (id != null && parent != null) {
			locationParents.put(id, parent);
			reference("locations", id, "ParentID", parent, "locations");
		}
	}

	private void acceptInspection(JsonNode row, String id) {
		reference("inspections", id, "CustomerID", text(row, "CustomerID"), "customers");
		reference("inspections", id, "CustomerSiteID", text(row, "CustomerSiteID"), "customerSites");
		String unit = text(row, "TemperatureUnit");
		if (unit == null) {
			issue(ValidationSeverity.WARNING, "TEMPERATURE_UNIT_MISSING", "inspections", id,
				"La unidad requiere validación con datos reales");
		} else if (!KNOWN_TEMPERATURE_UNITS.contains(unit.toUpperCase(Locale.ROOT))) {
			issue(ValidationSeverity.WARNING, "UNKNOWN_TEMPERATURE_UNIT", "inspections", id,
				"Unidad de temperatura desconocida: " + unit);
		}
	}

	private void acceptProblem(JsonNode row, String id) {
		reference("problems", id, "LocationID", text(row, "LocationID"), "locations");
		String prior = text(row, "PriorProblemID");
		boolean chronic = row.path("IsChronic").asBoolean(false) || "1".equals(text(row, "IsChronic"));
		if (chronic) {
			chronicProblems++;
		}
		if (id != null && prior != null && !id.equalsIgnoreCase(prior)) {
			problemPriors.put(id, prior);
			reference("problems", id, "PriorProblemID", prior, "problems");
		}
		if (id != null && id.equalsIgnoreCase(prior)) {
			issue(ValidationSeverity.WARNING, "SELF_PRIOR_PROBLEM", "problems", id,
				"PriorProblemID apunta al mismo problema y se tratará como inicio del historial");
		}
		if (chronic && prior == null) {
			issue(ValidationSeverity.WARNING, "CHRONIC_WITHOUT_PRIOR", "problems", id,
				"IsChronic está activo pero PriorProblemID está vacío; requiere regla/datos reales");
		}
	}

	private void reference(String dataset, String recordId, String field, String value, String targetDataset) {
		if (value != null) {
			references.add(new Reference(dataset, recordId, field, value, targetDataset));
		}
	}

	private void validateReferences() {
		for (Reference reference : references) {
			if (!ids.getOrDefault(reference.targetDataset, Set.of()).contains(reference.value)) {
				issue(ValidationSeverity.ERROR, "MISSING_REFERENCE", reference.dataset, reference.recordId,
					reference.field + " no existe en " + reference.targetDataset + ": " + reference.value);
			}
		}
	}

	private void validateCycles(Map<String, String> edges, String code, String dataset) {
		Set<String> fullyVisited = new HashSet<>();
		for (String start : edges.keySet()) {
			if (fullyVisited.contains(start)) {
				continue;
			}
			Set<String> path = new HashSet<>();
			String current = start;
			while (current != null && edges.containsKey(current) && !fullyVisited.contains(current)) {
				if (!path.add(current)) {
					issue(ValidationSeverity.ERROR, code, dataset, current, "Se detectó un ciclo de referencias");
					break;
				}
				current = edges.get(current);
			}
			fullyVisited.addAll(path);
		}
	}

	private String text(JsonNode row, String field) {
		if (field == null) {
			return null;
		}
		JsonNode value = row.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asText().trim();
		return text.isEmpty() ? null : text;
	}

	private long count(String dataset) {
		return counts.getOrDefault(dataset, 0L);
	}

	private void issue(ValidationSeverity severity, String code, String dataset, String recordId, String message) {
		if (issues.size() < MAX_REPORTED_ISSUES) {
			issues.add(new LegacyValidationIssue(severity, code, dataset, recordId, message));
		} else if (issues.size() == MAX_REPORTED_ISSUES) {
			issues.add(new LegacyValidationIssue(ValidationSeverity.INFO, "ISSUES_TRUNCATED", null, null,
				"Se alcanzó el límite de alertas detalladas"));
		}
	}

	private record Reference(String dataset, String recordId, String field, String value, String targetDataset) {
	}
}
