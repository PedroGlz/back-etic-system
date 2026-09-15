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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
	private final Map<String, List<Map<String, Object>>> datasets = new LinkedHashMap<>();
	private final Map<String, Map<String, Map<String, Object>>> indexes = new LinkedHashMap<>();
	private final Map<String, Set<String>> persistedSourceIds = new LinkedHashMap<>();
	private final Map<String, PendingTable> pendingTables = new LinkedHashMap<>();
	private long batches;

	public LegacyEtlContext(String importId, Path datasetsDirectory, LegacyDatasetStore store,
		LegacyBatchUpsertRepository repository, LegacyEtlReportBuilder report, int batchSize) {
		this.importId = importId;
		this.datasetsDirectory = datasetsDirectory;
		this.store = store;
		this.repository = repository;
		this.report = report;
		this.batchSize = batchSize;
	}

	public void forEach(String dataset, Consumer<Map<String, Object>> consumer) { dataset(dataset).forEach(consumer); }
	public Map<String, Map<String, Object>> index(String dataset, String idField) {
		String cacheKey = dataset + '\u0000' + idField;
		return indexes.computeIfAbsent(cacheKey, ignored -> {
			Map<String, Map<String, Object>> result = new LinkedHashMap<>();
			dataset(dataset).forEach(row -> { String id = text(row, idField); if (id != null) result.put(id, row); });
			return result;
		});
	}
	private List<Map<String, Object>> dataset(String name) {
		return datasets.computeIfAbsent(name, ignored -> {
			List<Map<String, Object>> rows = new ArrayList<>();
			store.forEach(datasetsDirectory, name, rows::add);
			return rows;
		});
	}
	public void release(String... names) {
		for (String name : names) {
			datasets.remove(name);
			String prefix = name + '\u0000';
			indexes.keySet().removeIf(key -> key.startsWith(prefix));
		}
	}
	public void releaseShared(String... names) { for (String name : names) shared.remove(name); }
	public void finishPhase() { persistedSourceIds.clear(); }
	public long batches() { return batches; }
	public void releaseAll() { datasets.clear(); indexes.clear(); shared.clear(); persistedSourceIds.clear(); pendingTables.clear(); }
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
		Set<String> seen = persistedSourceIds.computeIfAbsent(table, ignored -> new HashSet<>());
		for (Map<String, Object> row : batch) {
			Object rawId = row.get(key);
			String id = rawId == null ? "" : rawId.toString().trim();
			if (id.isEmpty()) throw new IllegalStateException("ID obligatorio ausente para " + table + "." + key);
			if (!seen.add(id.toUpperCase(Locale.ROOT)))
				throw new IllegalStateException("ID duplicado en el origen para " + table + ": " + id);
		}
		PendingTable pending=pendingTables.computeIfAbsent(table,ignored->new PendingTable(key,new ArrayList<>()));
		if(!pending.key().equalsIgnoreCase(key))throw new IllegalStateException("Clave inconsistente para "+table);
		pending.rows().addAll(batch);batch.clear();
	}
	public void flushPending() {
		for(Map.Entry<String,PendingTable> entry:pendingTables.entrySet()){
			String table=entry.getKey();PendingTable pending=entry.getValue();
			LegacyBatchUpsertRepository.BatchResult result=repository.upsert(table,pending.key(),pending.rows(),batchSize);
			batches+=result.batchesExecuted();
			report.inserted(table,result.inserted());report.updated(table,result.updated());
			result.insertedIds().forEach(id->report.persisted(table,id,"INSERTED"));
			result.updatedIds().forEach(id->report.persisted(table,id,"UPDATED"));
			for(long index=0;index<result.missingId();index++)report.skipped(table);
			result.skippedIds().forEach(item->report.skipped(table,item.id(),item.reason().name(),item.sourceDate(),item.destinationDate()));
		}
		pendingTables.clear();
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
	private record PendingTable(String key,List<Map<String,Object>> rows){}
}
