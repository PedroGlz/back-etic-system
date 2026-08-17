package com.etic.system.legacyimport.transform;

import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.report.LegacyEtlReportBuilder;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class LegacyEtlContext {

	private final String importId;
	private final Path datasetsDirectory;
	private final LegacyDatasetStore store;
	private final LegacyBatchUpsertRepository repository;
	private final LegacyEtlReportBuilder report;
	private final int batchSize;
	private final Map<String, Object> shared = new LinkedHashMap<>();

	public LegacyEtlContext(String importId, Path datasetsDirectory, LegacyDatasetStore store,
		LegacyBatchUpsertRepository repository, LegacyEtlReportBuilder report, int batchSize) {
		this.importId = importId;
		this.datasetsDirectory = datasetsDirectory;
		this.store = store;
		this.repository = repository;
		this.report = report;
		this.batchSize = batchSize;
	}

	public void forEach(String dataset, Consumer<Map<String, Object>> consumer) { store.forEach(datasetsDirectory, dataset, consumer); }
	public Map<String, Map<String, Object>> index(String dataset, String idField) {
		Map<String, Map<String, Object>> result = new LinkedHashMap<>();
		forEach(dataset, row -> { String id = text(row, idField); if (id != null) result.put(id, row); });
		return result;
	}
	public void transform(String dataset, String table, String key, Function<Map<String, Object>, Map<String, Object>> mapper) {
		List<Map<String, Object>> batch = new ArrayList<>(batchSize);
		forEach(dataset, source -> {
			report.source(table);
			Map<String, Object> target = mapper.apply(source);
			if (target == null || target.isEmpty()) { report.skipped(table); return; }
			batch.add(target);
			if (batch.size() >= batchSize) flush(table, key, batch);
		});
		flush(table, key, batch);
	}
	public void flush(String table, String key, List<Map<String, Object>> batch) {
		if (batch.isEmpty()) return;
		LegacyBatchUpsertRepository.BatchResult result = repository.upsert(table, key, batch);
		report.inserted(table, result.inserted()); report.updated(table, result.updated());
		result.insertedIds().forEach(id->report.persisted(table,id,"INSERTED"));
		result.updatedIds().forEach(id->report.persisted(table,id,"UPDATED"));
		for (long index = 0; index < result.missingId(); index++) report.skipped(table);
		result.skippedIds().forEach(item->report.skipped(table,item.id(),item.reason().name(),item.sourceDate(),item.destinationDate()));
		batch.clear();
	}
	public String text(Map<String, Object> row, String key) {
		Object value = row.get(key); if (value == null || value.toString().isBlank()) return null; return value.toString().trim();
	}
	public Object value(Map<String, Object> row, String key) { return row.get(key); }
	public LocalDateTime date(Map<String, Object> row, String key) {
		String value = text(row, key); if (value == null) return null;
		try { return LocalDateTime.parse(value.replace(" ", "T")); } catch (DateTimeParseException exception) { return null; }
	}
	public Map<String, Object> row(Object... values) {
		Map<String, Object> row = new LinkedHashMap<>();
		for (int index = 0; index < values.length; index += 2) if (values[index + 1] != null) {
			String column=(String)values[index];Object value=values[index+1];
			if(value instanceof String text&&(column.toLowerCase(java.util.Locale.ROOT).contains("fecha")||column.toLowerCase(java.util.Locale.ROOT).endsWith("_date")))value=text.replace('T',' ');
			row.put(column,value);
		}
		return row;
	}
	public String importId() { return importId; }
	public LegacyEtlReportBuilder report() { return report; }
	public LegacyBatchUpsertRepository repository() { return repository; }
	public Map<String, Object> shared() { return shared; }
}
