package com.etic.system.legacyimport.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegacyEtlReportBuilder {
	private static final int MAX_WARNING_DETAILS = 10_000;

	private final String importId;
	private final Map<String, MutableStats> tables = new LinkedHashMap<>();
	private final List<String> warnings = new ArrayList<>();
	private final List<LegacyRecordOutcome> outcomes = new ArrayList<>();
	private long legacyProblems;
	private long problemInspections;
	private long pieProblemInspections;
	private long targetProblems;
	private long chronicFamilies;
	private long historyRelations;
	private long additionalPhotos;
	private long unknownTemperatureUnits;

	public LegacyEtlReportBuilder(String importId) { this.importId = importId; }
	public void source(String table) { stats(table).source++; }
	public void inserted(String table, long count) { stats(table).inserted += count; }
	public void updated(String table, long count) { stats(table).updated += count; }
	public void persisted(String table,String id,String action) { outcomes.add(new LegacyRecordOutcome(table,id,action,null,null,null)); }
	public void skipped(String table) { stats(table).skipped++; }
	public void skipped(String table, String id, String reason, java.time.LocalDateTime sourceDate, java.time.LocalDateTime destinationDate) {
		MutableStats stats=stats(table);stats.skipped++;
		switch(reason){case "DESTINATION_NEWER"->stats.skippedDestinationNewer++;case "SAME_DATE"->stats.skippedSameDate++;case "NO_COMPARABLE_DATE"->stats.skippedNoComparableDate++;default->{} }
		outcomes.add(new LegacyRecordOutcome(table,id,"SKIPPED",reason,sourceDate,destinationDate));
	}
	public void warning(String table, String message) { stats(table).warnings++; if(warnings.size()<MAX_WARNING_DETAILS) warnings.add(message); }
	public void error(String table) { stats(table).errors++; }
	public void orphan(String table) { stats(table).orphans++; }
	public void orphans(String table, long count) { stats(table).orphans += count; }
	public void legacyProblems(long value) { legacyProblems = value; }
	public void problemInspections(long value) { problemInspections = value; }
	public void pieProblemInspections(long value) { pieProblemInspections = value; }
	public void targetProblems(long value) { targetProblems = value; }
	public void chronicFamilies(long value) { chronicFamilies = value; }
	public void historyRelations(long value) { historyRelations = value; }
	public void additionalPhotos(long value) { additionalPhotos += value; }
	public void unknownTemperatureUnits(long value) { unknownTemperatureUnits += value; }
	public void assertBalanced() {
		for (Map.Entry<String,MutableStats> entry:tables.entrySet()) {
			MutableStats value=entry.getValue();long reconciled=value.inserted+value.updated+value.skipped+value.errors;
			if(value.source!=reconciled)throw new IllegalStateException("Conciliación incompleta en "+entry.getKey()+": origen="+value.source+", procesados="+reconciled);
			if(value.source>0&&reconciled==0)throw new IllegalStateException("La tabla "+entry.getKey()+" tiene origen pero ninguna acción persistente");
		}
	}
	public Checkpoint checkpoint() {
		Map<String,MutableStats> saved=new LinkedHashMap<>();tables.forEach((table,stats)->saved.put(table,stats.copy()));
		return new Checkpoint(saved,warnings.size(),outcomes.size(),legacyProblems,problemInspections,pieProblemInspections,targetProblems,chronicFamilies,historyRelations,additionalPhotos,unknownTemperatureUnits);
	}
	public void restore(Checkpoint value) {
		tables.clear();value.tables.forEach((table,stats)->tables.put(table,stats.copy()));truncate(warnings,value.warningSize);truncate(outcomes,value.outcomeSize);
		legacyProblems=value.legacyProblems;problemInspections=value.problemInspections;pieProblemInspections=value.pieProblemInspections;targetProblems=value.targetProblems;chronicFamilies=value.chronicFamilies;historyRelations=value.historyRelations;additionalPhotos=value.additionalPhotos;unknownTemperatureUnits=value.unknownTemperatureUnits;
	}
	private void truncate(List<?> values,int size){if(values.size()>size)values.subList(size,values.size()).clear();}
	public long sourceCount(){return tables.values().stream().mapToLong(stats->stats.source).sum();}

	public LegacyEtlReport build() {
		List<TableReconciliation> rows = tables.entrySet().stream().map(entry -> entry.getValue().toRecord(entry.getKey())).toList();
		return new LegacyEtlReport(importId, rows, legacyProblems, problemInspections, pieProblemInspections,
			targetProblems, chronicFamilies, historyRelations, additionalPhotos, unknownTemperatureUnits, List.copyOf(outcomes), List.copyOf(warnings));
	}

	private MutableStats stats(String table) { return tables.computeIfAbsent(table, ignored -> new MutableStats()); }

	private static class MutableStats {
		long source, inserted, updated, skipped, skippedDestinationNewer, skippedSameDate, skippedNoComparableDate, warnings, errors, orphans;
		TableReconciliation toRecord(String table) {
			return new TableReconciliation(table, source, inserted, updated, skipped, skippedDestinationNewer, skippedSameDate, skippedNoComparableDate, warnings, errors, orphans);
		}
		MutableStats copy(){MutableStats value=new MutableStats();value.source=source;value.inserted=inserted;value.updated=updated;value.skipped=skipped;value.skippedDestinationNewer=skippedDestinationNewer;value.skippedSameDate=skippedSameDate;value.skippedNoComparableDate=skippedNoComparableDate;value.warnings=warnings;value.errors=errors;value.orphans=orphans;return value;}
	}
	public static final class Checkpoint {
		private final Map<String,MutableStats> tables;private final int warningSize,outcomeSize;
		private final long legacyProblems,problemInspections,pieProblemInspections,targetProblems,chronicFamilies,historyRelations,additionalPhotos,unknownTemperatureUnits;
		private Checkpoint(Map<String,MutableStats> tables,int warningSize,int outcomeSize,long legacyProblems,long problemInspections,long pieProblemInspections,long targetProblems,long chronicFamilies,long historyRelations,long additionalPhotos,long unknownTemperatureUnits){this.tables=tables;this.warningSize=warningSize;this.outcomeSize=outcomeSize;this.legacyProblems=legacyProblems;this.problemInspections=problemInspections;this.pieProblemInspections=pieProblemInspections;this.targetProblems=targetProblems;this.chronicFamilies=chronicFamilies;this.historyRelations=historyRelations;this.additionalPhotos=additionalPhotos;this.unknownTemperatureUnits=unknownTemperatureUnits;}
	}
}
