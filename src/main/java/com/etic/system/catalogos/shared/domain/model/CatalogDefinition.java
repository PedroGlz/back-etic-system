package com.etic.system.catalogos.shared.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CatalogDefinition(
	CatalogSchema schema,
	String table,
	String idColumn,
	LinkedHashMap<String, String> columns,
	String orderField,
	boolean audited
) {

	public static CatalogDefinition of(
		String key,
		String title,
		String table,
		String idColumn,
		String orderField,
		boolean audited,
		List<CatalogField> fields,
		String... mappings
	) {
		LinkedHashMap<String, String> columns = new LinkedHashMap<>();
		for (int index = 0; index < mappings.length; index += 2) {
			columns.put(mappings[index], mappings[index + 1]);
		}
		return new CatalogDefinition(new CatalogSchema(key, title, fields), table, idColumn, columns, orderField, audited);
	}

	public Map<String, String> columnsView() {
		return Map.copyOf(columns);
	}

	public CatalogDefinition withWorkflowColumns() {
		LinkedHashMap<String, String> extendedColumns = new LinkedHashMap<>(columns);
		extendedColumns.put("recordType", "Tipo_Registro");
		extendedColumns.put("sourceRecordId", "Id_Registro_Origen");
		extendedColumns.put("inspectionId", "Id_Inspeccion");
		extendedColumns.put("historical", "Es_Historico");
		extendedColumns.put("captureOrigin", "Origen_Captura");
		extendedColumns.put("reviewComment", "Comentario_Revision");
		extendedColumns.put("reviewedBy", "Revisado_Por");
		extendedColumns.put("reviewedAt", "Fecha_Revision");
		return new CatalogDefinition(schema, table, idColumn, extendedColumns, orderField, audited);
	}

	public CatalogDefinition withColumn(String field, String column) {
		LinkedHashMap<String, String> extendedColumns = new LinkedHashMap<>(columns);
		extendedColumns.put(field, column);
		return new CatalogDefinition(schema, table, idColumn, extendedColumns, orderField, audited);
	}
}
