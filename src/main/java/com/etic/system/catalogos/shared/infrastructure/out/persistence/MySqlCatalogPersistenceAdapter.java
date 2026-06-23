package com.etic.system.catalogos.shared.infrastructure.out.persistence;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogField;
import com.etic.system.catalogos.shared.domain.model.CatalogRecord;
import com.etic.system.catalogos.shared.domain.port.out.CatalogPersistencePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class MySqlCatalogPersistenceAdapter implements CatalogPersistencePort {

	private static final String ACTIVE = "Activo";
	private static final String INACTIVE = "Inactivo";

	private final NamedParameterJdbcTemplate jdbc;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public MySqlCatalogPersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<CatalogRecord> findAll(CatalogDefinition definition) {
		String sql = "SELECT " + selectColumns(definition) + " FROM " + definition.table()
			+ " ORDER BY " + definition.columns().get(definition.orderField());
		return jdbc.query(sql, Map.of(), (rs, rowNum) -> mapRecord(rs, definition));
	}

	@Override
	public Optional<CatalogRecord> findById(CatalogDefinition definition, String id) {
		String sql = "SELECT " + selectColumns(definition) + " FROM " + definition.table()
			+ " WHERE " + definition.idColumn() + " = :id";
		return jdbc.query(sql, Map.of("id", id), (rs, rowNum) -> mapRecord(rs, definition)).stream().findFirst();
	}

	@Override
	@Transactional
	public CatalogRecord create(CatalogDefinition definition, Map<String, Object> values, String userId) {
		String id = UUID.randomUUID().toString().toUpperCase();
		List<String> columns = new ArrayList<>();
		List<String> parameters = new ArrayList<>();
		MapSqlParameterSource params = new MapSqlParameterSource("id", id);
		columns.add(definition.idColumn());
		parameters.add(":id");
		addValues(definition, values, columns, parameters, params);
		columns.add("Estatus");
		parameters.add(":status");
		params.addValue("status", ACTIVE);
		if (definition.audited()) {
			columns.add("Creado_Por");
			columns.add("Fecha_Creacion");
			parameters.add(":userId");
			parameters.add(":now");
			params.addValue("userId", userId).addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		}
		clearDefaultIfNeeded(definition, values, userId);
		jdbc.update("INSERT INTO " + definition.table() + " (" + String.join(", ", columns)
			+ ") VALUES (" + String.join(", ", parameters) + ")", params);
		return findById(definition, id).orElseThrow();
	}

	@Override
	@Transactional
	public CatalogRecord update(CatalogDefinition definition, String id, Map<String, Object> values, String userId) {
		List<String> assignments = new ArrayList<>();
		MapSqlParameterSource params = new MapSqlParameterSource("id", id);
		for (Map.Entry<String, String> column : definition.columns().entrySet()) {
			if (!column.getKey().equals("id") && !column.getKey().equals("status")) {
				Object value = values.get(column.getKey());
				if (isWriteOnly(definition, column.getKey()) && (value == null || value.toString().isBlank())) {
					continue;
				}
				assignments.add(column.getValue() + " = :" + column.getKey());
				params.addValue(column.getKey(), passwordValue(column.getKey(), value));
			}
		}
		if (definition.audited()) {
			assignments.add("Modificado_Por = :userId");
			assignments.add("Fecha_Mod = :now");
			params.addValue("userId", userId).addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		}
		clearDefaultIfNeeded(definition, values, userId);
		jdbc.update("UPDATE " + definition.table() + " SET " + String.join(", ", assignments)
			+ " WHERE " + definition.idColumn() + " = :id", params);
		return findById(definition, id).orElseThrow();
	}

	@Override
	public void deactivate(CatalogDefinition definition, String id, String userId) {
		String audit = definition.audited() ? ", Modificado_Por = :userId, Fecha_Mod = :now" : "";
		MapSqlParameterSource params = new MapSqlParameterSource("id", id)
			.addValue("status", INACTIVE)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("UPDATE " + definition.table() + " SET Estatus = :status" + audit
			+ " WHERE " + definition.idColumn() + " = :id", params);
	}

	@Override
	public boolean activeReferenceExists(CatalogDefinition definition, String id) {
		Integer count = jdbc.queryForObject(
			"SELECT COUNT(*) FROM " + definition.table() + " WHERE " + definition.idColumn() + " = :id AND Estatus = :status",
			Map.of("id", id, "status", ACTIVE),
			Integer.class
		);
		return count != null && count > 0;
	}

	private CatalogRecord mapRecord(java.sql.ResultSet rs, CatalogDefinition definition) throws java.sql.SQLException {
		Map<String, Object> values = new LinkedHashMap<>();
		for (CatalogField field : definition.schema().fields()) {
			if (!field.writeOnly()) {
				values.put(field.name(), rs.getObject(field.name()));
			}
		}
		values.put("id", rs.getObject("id"));
		values.put("status", rs.getObject("status"));
		return new CatalogRecord(values);
	}

	private String selectColumns(CatalogDefinition definition) {
		return definition.columns().entrySet().stream()
			.filter(entry -> !isWriteOnly(definition, entry.getKey()))
			.map(entry -> entry.getValue() + " AS " + entry.getKey())
			.collect(Collectors.joining(", "));
	}

	private void addValues(
		CatalogDefinition definition,
		Map<String, Object> values,
		List<String> columns,
		List<String> parameters,
		MapSqlParameterSource params
	) {
		for (Map.Entry<String, String> column : definition.columns().entrySet()) {
			if (!column.getKey().equals("id") && !column.getKey().equals("status")) {
				columns.add(column.getValue());
				parameters.add(":" + column.getKey());
				Object value = values.get(column.getKey());
				params.addValue(column.getKey(), passwordValue(column.getKey(), value));
			}
		}
	}

	private boolean isWriteOnly(CatalogDefinition definition, String fieldName) {
		return definition.schema().fields().stream()
			.anyMatch(field -> field.name().equals(fieldName) && field.writeOnly());
	}

	private Object passwordValue(String fieldName, Object value) {
		if (!fieldName.equals("password") || value == null || value.toString().isBlank()) {
			return value;
		}
		return passwordEncoder.encode(value.toString());
	}

	private void clearDefaultIfNeeded(CatalogDefinition definition, Map<String, Object> values, String userId) {
		Optional<CatalogField> defaultField = defaultField(definition);
		if (defaultField.isEmpty() || !"1".equals(String.valueOf(values.get(defaultField.get().name())))) {
			return;
		}

		String column = definition.columns().get(defaultField.get().name());
		String audit = definition.audited() ? ", Modificado_Por = :userId, Fecha_Mod = :now" : "";
		MapSqlParameterSource params = new MapSqlParameterSource("value", "0")
			.addValue("currentValue", "1")
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("UPDATE " + definition.table() + " SET " + column + " = :value" + audit
			+ " WHERE " + column + " = :currentValue", params);
	}

	private Optional<CatalogField> defaultField(CatalogDefinition definition) {
		return definition.schema().fields().stream()
			.filter(field -> "boolean".equals(field.type()) && "isDefault".equals(field.name()))
			.findFirst();
	}
}
