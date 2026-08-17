package com.etic.system.legacyimport.report;

public record TableReconciliation(
	String table,
	long source,
	long inserted,
	long updated,
	long skipped,
	long skippedDestinationNewer,
	long skippedSameDate,
	long skippedNoComparableDate,
	long warnings,
	long errors,
	long orphans
) {
}
