package com.etic.system.legacyimport.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class LegacyBatchUpsertRepository {

	private final JdbcTemplate jdbc;
	private final Map<String, TableMetadata> metadataCache = new ConcurrentHashMap<>();

	public LegacyBatchUpsertRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public BatchResult upsert(String table, String keyColumn, List<Map<String, Object>> sourceRows) {
		if (sourceRows.isEmpty()) return BatchResult.empty();
		TableMetadata metadata = metadata(table);
		String canonicalKey = metadata.canonical(keyColumn);
		if (canonicalKey == null) throw new IllegalStateException("La tabla " + table + " no contiene " + keyColumn);
		List<Map<String, Object>> rows = sourceRows.stream().map(row -> filter(row, metadata)).filter(row -> row.containsKey(canonicalKey)).toList();
		if (rows.isEmpty()) return BatchResult.missingIds(sourceRows.size());
		Map<String, ExistingRow> existing = existingRows(table, canonicalKey, metadata, rows.stream().map(row -> row.get(canonicalKey)).toList());
		List<Map<String,Object>> inserts=new ArrayList<>(), updates=new ArrayList<>(); List<SkippedId> skipped=new ArrayList<>();
		for(Map<String,Object> row:rows){String id=String.valueOf(row.get(canonicalKey));ExistingRow destination=existing.get(normalizedKey(id));
			if(destination==null){inserts.add(row);continue;}
			LocalDateTime sourceDate=comparisonDate(row,metadata);LocalDateTime destinationDate=destination.comparisonDate();
			if(sourceDate==null&&destinationDate==null){skipped.add(new SkippedId(id,SkipReason.NO_COMPARABLE_DATE,null,null));continue;}
			if(sourceDate==null){skipped.add(new SkippedId(id,SkipReason.DESTINATION_NEWER,null,destinationDate));continue;}
			if(destinationDate==null||sourceDate.isAfter(destinationDate)){updates.add(row);continue;}
			if(sourceDate.isEqual(destinationDate))skipped.add(new SkippedId(id,SkipReason.SAME_DATE,sourceDate,destinationDate));
			else skipped.add(new SkippedId(id,SkipReason.DESTINATION_NEWER,sourceDate,destinationDate));
		}
		executeGroups(table,canonicalKey,inserts,false);executeGroups(table,canonicalKey,updates,true);
		List<Object> affected=new ArrayList<>();inserts.forEach(row->affected.add(row.get(canonicalKey)));updates.forEach(row->affected.add(row.get(canonicalKey)));
		Set<String> persisted=existingKeyStrings(table,canonicalKey,affected);if(persisted.size()!=affected.size())throw new IllegalStateException("MySQL no confirmó todos los IDs escritos en "+table);
		long missingId=sourceRows.size()-rows.size();
		return new BatchResult(inserts.size(),updates.size(),missingId,skipped,
			inserts.stream().map(row->String.valueOf(row.get(canonicalKey))).toList(),
			updates.stream().map(row->String.valueOf(row.get(canonicalKey))).toList());
	}

	public boolean tableExists(String table) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", Integer.class, table);
		return count != null && count > 0;
	}

	public void updateDerived(String table,String keyColumn,List<Map<String,Object>> sourceRows){if(sourceRows.isEmpty())return;TableMetadata metadata=metadata(table);String key=metadata.canonical(keyColumn);if(key==null)throw new IllegalStateException("La tabla "+table+" no contiene "+keyColumn);List<Map<String,Object>> rows=sourceRows.stream().map(row->filter(row,metadata)).filter(row->row.containsKey(key)).toList();executeGroups(table,key,rows,true);Set<String> persisted=existingKeyStrings(table,key,rows.stream().map(row->row.get(key)).toList());if(persisted.size()!=rows.size())throw new IllegalStateException("MySQL no confirmó la actualización derivada en "+table);}

	public List<Map<String, Object>> query(String sql, Object... arguments) {
		return jdbc.queryForList(sql, arguments);
	}

	private void executeGroups(String table,String key,List<Map<String,Object>> rows,boolean update){Map<List<String>,List<Map<String,Object>>> groups=new LinkedHashMap<>();for(Map<String,Object> row:rows)groups.computeIfAbsent(List.copyOf(row.keySet()),ignored->new ArrayList<>()).add(row);for(var group:groups.entrySet())executeBatch(table,key,group.getKey(),group.getValue(),update);}
	private void executeBatch(String table, String key, List<String> columns, List<Map<String, Object>> rows,boolean update) {
		if(rows.isEmpty())return;
		String placeholders = String.join(",", columns.stream().map(ignored -> "?").toList());
		String sql;if(update){List<String> mutable=columns.stream().filter(column->!column.equalsIgnoreCase(key)).toList();if(mutable.isEmpty())return;sql="UPDATE "+table+" SET "+String.join(",",mutable.stream().map(column->column+"=?").toList())+" WHERE "+key+"=?";jdbc.batchUpdate(sql,rows,rows.size(),(statement,row)->bindUpdate(statement,mutable,key,row));}
		else{sql="INSERT INTO "+table+" ("+String.join(",",columns)+") VALUES ("+placeholders+")";jdbc.batchUpdate(sql,rows,rows.size(),(statement,row)->bind(statement,columns,row));}
	}

	private void bind(PreparedStatement statement, List<String> columns, Map<String, Object> row) throws SQLException {
		for (int index = 0; index < columns.size(); index++) statement.setObject(index + 1, row.get(columns.get(index)));
	}
	private void bindUpdate(PreparedStatement statement,List<String> columns,String key,Map<String,Object> row)throws SQLException{int index=1;for(String column:columns)statement.setObject(index++,row.get(column));statement.setObject(index,row.get(key));}

	private Set<Object> existingKeys(String table, String key, List<Object> keys) {
		Set<Object> result = new HashSet<>();
		for (int start = 0; start < keys.size(); start += 500) {
			List<Object> block = keys.subList(start, Math.min(start + 500, keys.size()));
			String placeholders = String.join(",", block.stream().map(ignored -> "?").toList());
			result.addAll(jdbc.queryForList("SELECT " + key + " FROM " + table + " WHERE " + key + " IN (" + placeholders + ")", Object.class, block.toArray()));
		}
		return result;
	}

	private Set<String> existingKeyStrings(String table,String key,List<Object> keys){Set<String> result=new HashSet<>();if(keys.isEmpty())return result;for(Object value:existingKeys(table,key,keys))result.add(normalizedKey(value));return result;}
	private Map<String,ExistingRow> existingRows(String table,String key,TableMetadata metadata,List<Object> keys){Map<String,ExistingRow> result=new HashMap<>();String modified=metadata.canonical("Fecha_Mod"),created=metadata.canonical("Fecha_Creacion");for(int start=0;start<keys.size();start+=500){List<Object> block=keys.subList(start,Math.min(start+500,keys.size()));String placeholders=String.join(",",block.stream().map(ignored->"?").toList());String columns=key+(modified==null?"":","+modified)+(created==null?"":","+created);for(Map<String,Object> row:jdbc.queryForList("SELECT "+columns+" FROM "+table+" WHERE "+key+" IN ("+placeholders+")",block.toArray())){LocalDateTime mod=modified==null?null:date(row.get(modified));LocalDateTime creation=created==null?null:date(row.get(created));result.put(normalizedKey(row.get(key)),new ExistingRow(mod!=null?mod:creation));}}return result;}
	private String normalizedKey(Object value){return String.valueOf(value).trim().toLowerCase(Locale.ROOT);}
	private LocalDateTime comparisonDate(Map<String,Object> row,TableMetadata metadata){String modified=metadata.canonical("Fecha_Mod"),created=metadata.canonical("Fecha_Creacion");LocalDateTime value=modified==null?null:date(row.get(modified));return value!=null?value:created==null?null:date(row.get(created));}
	private LocalDateTime date(Object value){if(value==null)return null;if(value instanceof LocalDateTime date)return date;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();if(value instanceof java.util.Date date)return new Timestamp(date.getTime()).toLocalDateTime();String text=value.toString().trim();if(text.isEmpty())return null;try{return LocalDateTime.parse(text.replace(" ","T"));}catch(DateTimeParseException exception){throw new IllegalArgumentException("Fecha de auditoría inválida: "+text,exception);}}

	private Map<String, Object> filter(Map<String, Object> row, TableMetadata metadata) {
		Map<String, Object> filtered = new LinkedHashMap<>();
		row.forEach((name, value) -> {
			String canonical = metadata.canonical(name);
			if (canonical != null) filtered.put(canonical, identifierValue(canonical, value));
		});
		return filtered;
	}

	private Object identifierValue(String column,Object value){
		if(!(value instanceof String text))return value;
		String name=column.toLowerCase(Locale.ROOT);
		boolean identifier=name.startsWith("id_")||name.equals("creado_por")||name.equals("modificado_por")
			||name.equals("problem_phase")||name.equals("reference_phase")||name.equals("additional_info")
			||name.equals("environment")||name.equals("cerrado_en_inspeccion");
		return identifier?text.toUpperCase(Locale.ROOT):value;
	}

	private TableMetadata metadata(String table) {
		return metadataCache.computeIfAbsent(table, name -> {
			Map<String, String> columns = new HashMap<>();
			for (Map<String, Object> row : jdbc.queryForList("SHOW COLUMNS FROM " + name)) {
				String field = String.valueOf(row.get("Field"));
				columns.put(field.toLowerCase(Locale.ROOT), field);
			}
			if (columns.isEmpty()) throw new IllegalStateException("Tabla MySQL inexistente o sin columnas: " + name);
			return new TableMetadata(columns);
		});
	}

	private record TableMetadata(Map<String, String> columns) {
		String canonical(String input) { return input == null ? null : columns.get(input.toLowerCase(Locale.ROOT)); }
	}

	private record ExistingRow(LocalDateTime comparisonDate){}
	public enum SkipReason{DESTINATION_NEWER,SAME_DATE,NO_COMPARABLE_DATE,MISSING_ID}
	public record SkippedId(String id,SkipReason reason,LocalDateTime sourceDate,LocalDateTime destinationDate){}
	public record BatchResult(long inserted,long updated,long missingId,List<SkippedId> skippedIds,List<String> insertedIds,List<String> updatedIds){public static BatchResult empty(){return new BatchResult(0,0,0,List.of(),List.of(),List.of());}public static BatchResult missingIds(long count){return new BatchResult(0,0,count,List.of(),List.of(),List.of());}public long skipped(){return missingId+skippedIds.size();}}
}
