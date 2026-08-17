package com.etic.system.legacyimport.report;

import java.util.List;

public record LegacyEtlReport(
	String importId,
	List<TableReconciliation> tables,
	long legacyProblems,
	long problemInspections,
	long pieProblemInspections,
	long targetProblems,
	long chronicFamilies,
	long historyRelations,
	long additionalPhotos,
	long unknownTemperatureUnits,
	List<LegacyRecordOutcome> outcomes,
	List<String> warnings
) {
}
