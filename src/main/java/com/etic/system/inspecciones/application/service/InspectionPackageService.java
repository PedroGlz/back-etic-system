package com.etic.system.inspecciones.application.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.reportes.plantillas.application.ReportTemplateService;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import com.etic.system.shared.util.DateValueNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class InspectionPackageService {

	private static final Set<String> ALLOWED_IMPORT_PATHS = Set.of(
		"inspection/inspecciones.json",
		"inspection/inspecciones_det.json",
		"inspection/datos_reporte.json",
		"inspection/problemas.json",
		"inspection/linea_base.json",
		"inspection/historial_problemas.json",
		"context/ubicaciones.json"
	);
	private static final Set<String> DELETE_ENABLED_PATHS = Set.of(
		"inspection/inspecciones_det.json",
		"context/ubicaciones.json"
	);
	private static final String UPSERT = "UPSERT";
	private static final String UPSERT_DELETE = "UPSERT_DELETE";

	private final JdbcTemplate jdbcTemplate;
	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper;
	private final ReportTemplateService reportTemplateService;
	private final Path exportsDirectory;

	public InspectionPackageService(
		JdbcTemplate jdbcTemplate,
		JdbcClient jdbcClient,
		ObjectMapper objectMapper,
		ReportTemplateService reportTemplateService,
		StorageProperties storageProperties
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
		this.reportTemplateService = reportTemplateService;
		this.exportsDirectory = storageProperties.basePath().resolve("inspecciones").resolve("exportaciones").normalize();
	}

	public ExportedInspectionPackage exportInspection(String inspectionId, String siteId, String fileName) {
		String safeFileName = sanitizeExportFileName(fileName);
		ensureExportsDirectory();
		Path exportPath = exportsDirectory.resolve(safeFileName).normalize();
		validateInsideExports(exportPath);

		Map<String, byte[]> entries = new LinkedHashMap<>();
		Map<String, Object> selectedInspection = singleRow(
			"SELECT * FROM inspecciones WHERE Id_Inspeccion = ?",
			inspectionId
		);
		if (selectedInspection == null) {
			throw new ResourceNotFoundException("No se encontró la inspección seleccionada para exportar");
		}

		Map<String, Object> selectedSite = singleRow(
			"SELECT * FROM sitios WHERE Id_Sitio = ?",
			siteId
		);
		if (selectedSite == null) {
			throw new BusinessValidationException("No se encontró el sitio seleccionado para exportar");
		}

		String clientId = asString(selectedSite.get("Id_Cliente"));
		String siteGroupId = asString(selectedSite.get("Id_Grupo_Sitios"));
		Map<String, Object> selectedClient = singleRow("SELECT * FROM clientes WHERE Id_Cliente = ?", clientId);
		Map<String, Object> selectedSiteGroup = singleRow("SELECT * FROM grupos_sitios WHERE Id_Grupo_Sitios = ?", siteGroupId);
		if (selectedClient == null || selectedSiteGroup == null) {
			throw new BusinessValidationException("No se encontró el contexto completo del sitio para exportar");
		}

		List<String> globalTables = configuredGlobalExportTables();
		entries.put("globals/export_table_scope.json", jsonBytes(payload(
			"export_table_scope",
			"GLOBAL_METADATA",
			Map.of("enabled", 1, "scope", "GLOBAL"),
			jdbcTemplate.queryForList("SELECT * FROM export_table_scope WHERE enabled = 1 AND scope = 'GLOBAL'")
		)));
		for (String tableName : globalTables) {
			List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + tableName);
			entries.put("globals/" + tableName + ".json", jsonBytes(payload(tableName, "GLOBAL", Map.of(), rows)));
		}

		if (tableExists("inspecciones")) {
			entries.put("inspection/inspecciones.json", jsonBytes(payload(
				"inspecciones",
				"INSPECTION",
				Map.of("Id_Sitio", siteId),
				jdbcTemplate.queryForList("SELECT * FROM inspecciones WHERE Id_Sitio = ?", siteId),
				List.of()
			)));
		}
		if (tableExists("inspecciones_det")) {
			entries.put("inspection/inspecciones_det.json", jsonBytes(payload(
				"inspecciones_det",
				"INSPECTION",
				Map.of("Id_Sitio", siteId),
				jdbcTemplate.queryForList("SELECT * FROM inspecciones_det WHERE Id_Sitio = ?", siteId),
				List.of()
			)));
		}
		if (tableExists("datos_reporte")) {
			entries.put("inspection/datos_reporte.json", jsonBytes(payload(
				"datos_reporte",
				"INSPECTION",
				Map.of("Id_Inspeccion", inspectionId),
				jdbcTemplate.queryForList("SELECT * FROM datos_reporte WHERE Id_Inspeccion = ?", inspectionId),
				List.of()
			)));
		}
		if (tableExists("problemas")) {
			entries.put("inspection/problemas.json", jsonBytes(payload(
				"problemas",
				"INSPECTION",
				Map.of("Id_Sitio", siteId),
				jdbcTemplate.queryForList("SELECT * FROM problemas WHERE Id_Sitio = ?", siteId),
				List.of()
			)));
		}
		if (tableExists("linea_base")) {
			entries.put("inspection/linea_base.json", jsonBytes(payload(
				"linea_base",
				"INSPECTION",
				Map.of("Id_Sitio", siteId),
				jdbcTemplate.queryForList("SELECT * FROM linea_base WHERE Id_Sitio = ?", siteId),
				List.of()
			)));
		}
		if (tableExists("historial_problemas")) {
			entries.put("inspection/historial_problemas.json", jsonBytes(payload(
				"historial_problemas",
				"INSPECTION",
				Map.of("Id_Sitio", siteId),
				jdbcTemplate.queryForList(
					"SELECT * FROM historial_problemas WHERE Id_Problema IN (SELECT Id_Problema FROM problemas WHERE Id_Sitio = ?)",
					siteId
				),
				List.of()
			)));
		}
		if (tableExists("ubicaciones")) {
			entries.put("context/ubicaciones.json", jsonBytes(payload(
				"ubicaciones",
				"CONTEXT",
				Map.of("Id_Sitio", siteId),
				jdbcTemplate.queryForList("SELECT * FROM ubicaciones WHERE Id_Sitio = ?", siteId),
				List.of()
			)));
		}

		entries.put("context/sitios.json", jsonBytes(payload("sitios", "CONTEXT", Map.of("Id_Sitio", siteId), List.of(selectedSite), List.of())));
		entries.put("context/clientes.json", jsonBytes(payload("clientes", "CONTEXT", Map.of("Id_Cliente", clientId), List.of(selectedClient), List.of())));
		entries.put("context/grupos_sitios.json", jsonBytes(payload("grupos_sitios", "CONTEXT", Map.of("Id_Grupo_Sitios", siteGroupId), List.of(selectedSiteGroup), List.of())));

		for (Path templatePath : reportTemplateService.filesForExport()) {
			try {
				entries.put("templates/" + templatePath.getFileName(), Files.readAllBytes(templatePath));
			} catch (IOException exception) {
				throw new BusinessValidationException("No fue posible incluir una plantilla de reportes en el paquete");
			}
		}

		Map<String, Object> manifest = new LinkedHashMap<>();
		manifest.put("package_type", "inspection_export");
		manifest.put("format_version", 1);
		manifest.put("schema_version", 1);
		manifest.put("source_system", "etic-springboot");
		manifest.put("target_system", "etic-kotlin");
		manifest.put("exported_at", Instant.now().toString());
		manifest.put("file_name", safeFileName);
		Map<String, Object> manifestInspection = new LinkedHashMap<>();
		manifestInspection.put("id_inspeccion", Objects.requireNonNullElse(selectedInspection.get("Id_Inspeccion"), inspectionId));
		manifestInspection.put("no_inspeccion", selectedInspection.get("No_Inspeccion"));
		manifestInspection.put("id_sitio", Objects.requireNonNullElse(selectedInspection.get("Id_Sitio"), siteId));
		manifestInspection.put("id_cliente", selectedInspection.get("Id_Cliente"));
		manifestInspection.put("id_grupo_sitios", selectedInspection.get("Id_Grupo_Sitios"));
		manifest.put("inspection", manifestInspection);
		manifest.put("files", new ArrayList<>(entries.keySet()));
		manifest.put("media_included", false);
		manifest.put("checksum_file", "checksums.json");

		byte[] manifestBytes = jsonBytes(manifest);
		Map<String, String> checksums = new LinkedHashMap<>();
		entries.forEach((path, content) -> checksums.put(path, "sha256:" + sha256(content)));
		checksums.put("manifest.json", "sha256:" + sha256(manifestBytes));
		byte[] checksumsBytes = jsonBytes(checksums);

		try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(exportPath))) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				writeZipEntry(zipOutputStream, entry.getKey(), entry.getValue());
			}
			writeZipEntry(zipOutputStream, "manifest.json", manifestBytes);
			writeZipEntry(zipOutputStream, "checksums.json", checksumsBytes);
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible crear el paquete ZIP de la inspección");
		}

		return new ExportedInspectionPackage(safeFileName, exportPath.toAbsolutePath().toString());
	}

	public Resource exportResource(String fileName) {
		ensureExportsDirectory();
		Path file = exportsDirectory.resolve(Path.of(fileName).getFileName()).normalize();
		validateInsideExports(file);
		if (!Files.exists(file) || !Files.isRegularFile(file)) {
			throw new ResourceNotFoundException("No se encontró el archivo de exportación solicitado");
		}
		return new FileSystemResource(file);
	}

	public ImportInspectionResult importInspectionResult(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessValidationException("Selecciona un paquete ZIP válido para importar");
		}
		String extension = extension(Objects.requireNonNullElse(file.getOriginalFilename(), ""));
		if (!"zip".equals(extension)) {
			throw new BusinessValidationException("Solo se permiten paquetes ZIP para la importación");
		}

		Map<String, byte[]> zipEntries = readZipEntries(file);
		Map<String, Object> manifest = parseJsonRequired(zipEntries, "manifest.json");
		Map<String, String> checksums = parseJsonRequired(zipEntries, "checksums.json");

		String packageType = asString(manifest.get("package_type"));
		if (!"inspection_result".equals(packageType)) {
			throw new BusinessValidationException("El paquete seleccionado no corresponde a un resultado de inspección");
		}

		String deltaMode = asString(manifest.getOrDefault("delta_mode", UPSERT)).toUpperCase(Locale.ROOT);
		if (!UPSERT.equals(deltaMode) && !UPSERT_DELETE.equals(deltaMode)) {
			throw new BusinessValidationException("El delta_mode del paquete no es compatible");
		}

		@SuppressWarnings("unchecked")
		List<String> filesInManifest = (List<String>) manifest.get("files");
		if (filesInManifest == null) {
			throw new BusinessValidationException("El manifest no contiene la lista de archivos");
		}

		validateChecksums(zipEntries, checksums, filesInManifest);

		List<String> processedPaths = new ArrayList<>();
		for (String path : filesInManifest) {
			if (!isAllowedImportPath(path)) {
				throw new BusinessValidationException("El paquete contiene un archivo no permitido: " + path);
			}
			if (isTemplatePath(path)) {
				continue;
			}
			Map<String, Object> payload = parseJsonRequired(zipEntries, path);
			String tableName = asString(payload.get("table"));
			if (tableName == null || tableName.isBlank()) {
				throw new BusinessValidationException("Uno de los archivos del paquete no declara su tabla");
			}
			if (!tableExists(tableName)) {
				continue;
			}
			applyPayload(tableName, payload, UPSERT_DELETE.equals(deltaMode) && isDeleteEnabled(path));
			processedPaths.add(path);
		}

		return new ImportInspectionResult(processedPaths.size(), processedPaths);
	}

	public Resource generateProblemsReport(String inspectionId, String startDate, String endDate) {
		Map<String, Object> inspection = singleRow(
			"SELECT i.No_Inspeccion, s.Sitio, c.Razon_Social FROM inspecciones i " +
				"LEFT JOIN sitios s ON s.Id_Sitio = i.Id_Sitio " +
				"LEFT JOIN clientes c ON c.Id_Cliente = i.Id_Cliente " +
				"WHERE i.Id_Inspeccion = ?",
			inspectionId
		);
		if (inspection == null) {
			throw new ResourceNotFoundException("No se encontró la inspección solicitada para generar el reporte");
		}

		ensureExportsDirectory();
		String siteName = sanitizeFragment(asString(inspection.get("Sitio")));
		String fileName = "ETIC_LISTADO_DE_PROBLEMAS_" + siteName + "_INSPECCION_" + Objects.toString(inspection.get("No_Inspeccion"), "") + ".csv";
		Path reportPath = exportsDirectory.resolve(fileName).normalize();
		validateInsideExports(reportPath);

		List<Map<String, Object>> rows;
		if (tableExists("problemas")) {
			rows = jdbcTemplate.queryForList(
				"SELECT * FROM problemas WHERE Id_Inspeccion = ? ORDER BY Numero_Problema",
				inspectionId
			);
		} else {
			rows = jdbcTemplate.queryForList(
				"SELECT Id_Inspeccion_Det, Id_Ubicacion, Id_Status_Inspeccion_Det, Notas_Inspeccion, Fecha_Creacion " +
					"FROM inspecciones_det WHERE Id_Inspeccion = ? ORDER BY Fecha_Creacion",
				inspectionId
			);
		}

		List<String> headers = rows.isEmpty() ? List.of("inspeccion", "cliente", "sitio", "fecha_inicio", "fecha_fin")
			: new ArrayList<>(rows.getFirst().keySet());

		StringBuilder csv = new StringBuilder();
		csv.append("Reporte,").append(escapeCsv("Listado de problemas")).append('\n');
		csv.append("Cliente,").append(escapeCsv(asString(inspection.get("Razon_Social")))).append('\n');
		csv.append("Sitio,").append(escapeCsv(asString(inspection.get("Sitio")))).append('\n');
		csv.append("Fecha inicio,").append(escapeCsv(startDate)).append('\n');
		csv.append("Fecha fin,").append(escapeCsv(endDate)).append('\n');
		csv.append('\n');
		csv.append(String.join(",", headers)).append('\n');
		for (Map<String, Object> row : rows) {
			List<String> values = new ArrayList<>();
			for (String header : headers) {
				values.add(escapeCsv(formatValue(row.get(header))));
			}
			csv.append(String.join(",", values)).append('\n');
		}

		try {
			Files.writeString(reportPath, csv.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible generar el reporte de problemas");
		}
		return new FileSystemResource(reportPath);
	}

	private void applyPayload(String tableName, Map<String, Object> payload, boolean deleteEnabled) {
		TableMeta meta = tableMeta(tableName);
		List<Map<String, Object>> rows = objectMapper.convertValue(payload.getOrDefault("rows", List.of()), new TypeReference<>() {});
		List<Map<String, Object>> deletedKeys = objectMapper.convertValue(payload.getOrDefault("deleted_keys", List.of()), new TypeReference<>() {});
		for (Map<String, Object> row : rows) {
			upsertRow(tableName, meta, row);
		}
		if (deleteEnabled) {
			for (Map<String, Object> deletedKey : deletedKeys) {
				deleteRow(tableName, meta, deletedKey);
			}
		}
	}

	private void upsertRow(String tableName, TableMeta meta, Map<String, Object> payload) {
		Map<String, Object> filtered = filterColumns(payload, meta);
		if (filtered.isEmpty()) {
			return;
		}
		Map<String, Object> primaryKeyValues = primaryKeyValues(meta, filtered);
		if (primaryKeyValues.size() != meta.primaryKeys.size()) {
			throw new BusinessValidationException("Falta la llave primaria en una fila del paquete para " + tableName);
		}

		boolean exists = existsByPrimaryKeys(tableName, meta.primaryKeys, primaryKeyValues);
		if (exists) {
			List<String> assignments = filtered.keySet().stream()
				.filter(column -> !meta.primaryKeys.contains(column))
				.map(column -> column + " = :" + column)
				.toList();
			if (assignments.isEmpty()) {
				return;
			}
			Map<String, Object> params = new HashMap<>(filtered);
			params.putAll(prefixPrimaryKeys(primaryKeyValues));
			String where = meta.primaryKeys.stream()
				.map(column -> column + " = :pk_" + column)
				.reduce((left, right) -> left + " AND " + right)
				.orElseThrow();
			jdbcClient.sql("UPDATE " + tableName + " SET " + String.join(", ", assignments) + " WHERE " + where)
				.params(params)
				.update();
			return;
		}

		String columns = String.join(", ", filtered.keySet());
		String placeholders = filtered.keySet().stream().map(column -> ":" + column).reduce((left, right) -> left + ", " + right).orElse("");
		jdbcClient.sql("INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")")
			.params(filtered)
			.update();
	}

	private void deleteRow(String tableName, TableMeta meta, Map<String, Object> deletedKey) {
		Map<String, Object> primaryKeyValues = primaryKeyValues(meta, deletedKey);
		if (primaryKeyValues.size() != meta.primaryKeys.size()) {
			return;
		}
		Map<String, Object> params = prefixPrimaryKeys(primaryKeyValues);
		String where = meta.primaryKeys.stream()
			.map(column -> column + " = :pk_" + column)
			.reduce((left, right) -> left + " AND " + right)
			.orElseThrow();
		if (meta.columns.contains("Estatus")) {
			params.put("status", "Inactivo");
			params.put("now", Timestamp.valueOf(LocalDateTime.now()));
			StringBuilder sql = new StringBuilder("UPDATE " + tableName + " SET Estatus = :status");
			if (meta.columns.contains("Fecha_Mod")) {
				sql.append(", Fecha_Mod = :now");
			}
			jdbcClient.sql(sql + " WHERE " + where).params(params).update();
			return;
		}
		jdbcClient.sql("DELETE FROM " + tableName + " WHERE " + where).params(params).update();
	}

	private boolean existsByPrimaryKeys(String tableName, List<String> primaryKeys, Map<String, Object> values) {
		Map<String, Object> params = prefixPrimaryKeys(values);
		String where = primaryKeys.stream()
			.map(column -> column + " = :pk_" + column)
			.reduce((left, right) -> left + " AND " + right)
			.orElseThrow();
		Integer count = jdbcClient.sql("SELECT COUNT(*) FROM " + tableName + " WHERE " + where)
			.params(params)
			.query(Integer.class)
			.single();
		return count != null && count > 0;
	}

	private Map<String, Object> filterColumns(Map<String, Object> payload, TableMeta meta) {
		Map<String, Object> filtered = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : payload.entrySet()) {
			if (meta.columns.contains(entry.getKey())) {
				Object value = meta.temporalColumns.contains(entry.getKey())
					? DateValueNormalizer.normalizeDatabaseDateValue(entry.getValue())
					: entry.getValue();
				filtered.put(entry.getKey(), value);
			}
		}
		return filtered;
	}

	private Map<String, Object> primaryKeyValues(TableMeta meta, Map<String, Object> row) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (String primaryKey : meta.primaryKeys) {
			if (row.containsKey(primaryKey)) {
				values.put(primaryKey, row.get(primaryKey));
			}
		}
		return values;
	}

	private Map<String, Object> prefixPrimaryKeys(Map<String, Object> values) {
		Map<String, Object> params = new LinkedHashMap<>();
		values.forEach((key, value) -> params.put("pk_" + key, value));
		return params;
	}

	private TableMeta tableMeta(String tableName) {
		if (!tableExists(tableName)) {
			throw new BusinessValidationException("La tabla " + tableName + " no existe en el backend actual");
		}
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW COLUMNS FROM " + tableName);
		List<String> columns = new ArrayList<>();
		List<String> temporalColumns = new ArrayList<>();
		List<String> primaryKeys = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			String field = Objects.toString(row.get("Field"), "");
			if (!field.isBlank()) {
				columns.add(field);
			}
			String type = Objects.toString(row.get("Type"), "").toLowerCase(Locale.ROOT);
			if (type.contains("date") || type.contains("time")) {
				temporalColumns.add(field);
			}
			if ("PRI".equals(Objects.toString(row.get("Key"), ""))) {
				primaryKeys.add(field);
			}
		}
		return new TableMeta(columns, temporalColumns, primaryKeys);
	}

	private Map<String, byte[]> readZipEntries(MultipartFile file) {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (InputStream inputStream = file.getInputStream(); ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				if (!zipEntry.isDirectory()) {
					entries.put(zipEntry.getName(), zipInputStream.readAllBytes());
				}
			}
			return entries;
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible leer el paquete ZIP seleccionado");
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T parseJsonRequired(Map<String, byte[]> entries, String path) {
		byte[] content = entries.get(path);
		if (content == null) {
			throw new BusinessValidationException("No se encontró el archivo " + path + " dentro del paquete");
		}
		try {
			return (T) objectMapper.readValue(content, Object.class);
		} catch (IOException exception) {
			throw new BusinessValidationException("El archivo " + path + " no contiene JSON válido");
		}
	}

	private void validateChecksums(Map<String, byte[]> entries, Map<String, String> checksums, List<String> filesInManifest) {
		for (String path : filesInManifest) {
			byte[] content = entries.get(path);
			if (content == null) {
				throw new BusinessValidationException("El paquete no contiene el archivo " + path);
			}
			String expected = checksums.get(path);
			String actual = "sha256:" + sha256(content);
			if (!actual.equals(expected)) {
				throw new BusinessValidationException("El checksum no coincide para " + path);
			}
		}
		byte[] manifest = entries.get("manifest.json");
		if (manifest == null || !("sha256:" + sha256(manifest)).equals(checksums.get("manifest.json"))) {
			throw new BusinessValidationException("El checksum del manifest no coincide");
		}
	}

	private boolean isAllowedImportPath(String path) {
		return ALLOWED_IMPORT_PATHS.contains(path)
			|| "globals/export_table_scope.json".equals(path)
			|| isTemplatePath(path)
			|| configuredGlobalExportTables().stream().anyMatch(table -> ("globals/" + table + ".json").equals(path));
	}

	private boolean isDeleteEnabled(String path) {
		return DELETE_ENABLED_PATHS.contains(path)
			|| configuredGlobalExportTables().stream().anyMatch(table -> ("globals/" + table + ".json").equals(path));
	}

	private boolean isTemplatePath(String path) {
		return path.startsWith("templates/") && !path.contains("..");
	}

	private List<String> configuredGlobalExportTables() {
		if (!tableExists("export_table_scope")) {
			throw new BusinessValidationException("La tabla export_table_scope es obligatoria para exportar catálogos globales");
		}

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
			"SELECT table_name FROM export_table_scope WHERE enabled = 1 AND scope = 'GLOBAL' ORDER BY table_name"
		);
		List<String> tableNames = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			String tableName = Objects.toString(row.get("table_name"), "").trim();
			if (!tableName.matches("[A-Za-z0-9_]+")) {
				throw new BusinessValidationException("Nombre de tabla no válido en export_table_scope: " + tableName);
			}
			if (!tableName.isBlank()) {
				tableNames.add(tableName);
			}
		}
		if (tableNames.isEmpty()) {
			throw new BusinessValidationException("No hay tablas globales habilitadas en export_table_scope");
		}
		return tableNames;
	}

	private Map<String, Object> payload(String table, String scope, Map<String, Object> filters, List<Map<String, Object>> rows) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("table", table);
		payload.put("scope", scope);
		payload.put("filters", filters);
		payload.put("row_count", rows.size());
		payload.put("rows", rows);
		return payload;
	}

	private Map<String, Object> payload(
		String table,
		String scope,
		Map<String, Object> filters,
		List<Map<String, Object>> rows,
		List<Map<String, Object>> deletedKeys
	) {
		return payload(table, scope, filters, rows);
	}

	private Map<String, Object> singleRow(String sql, Object... args) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private boolean tableExists(String tableName) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
			Integer.class,
			tableName
		);
		return count != null && count > 0;
	}

	private void writeZipEntry(ZipOutputStream zipOutputStream, String path, byte[] content) throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry(path));
		zipOutputStream.write(content);
		zipOutputStream.closeEntry();
	}

	private byte[] jsonBytes(Object value) {
		try {
			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible serializar el paquete de inspección");
		}
	}

	private String sha256(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(bytes);
			StringBuilder builder = new StringBuilder();
			for (byte value : hash) {
				builder.append(String.format("%02x", value));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private void ensureExportsDirectory() {
		try {
			Files.createDirectories(exportsDirectory);
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible preparar el directorio de exportaciones");
		}
	}

	private void validateInsideExports(Path file) {
		if (!file.toAbsolutePath().normalize().startsWith(exportsDirectory)) {
			throw new BusinessValidationException("La ruta del archivo de exportación no es válida");
		}
	}

	private String sanitizeExportFileName(String fileName) {
		String safeName = Path.of(Objects.requireNonNullElse(fileName, "inspeccion.zip")).getFileName().toString()
			.replaceAll("[\\\\/:*?\"<>|]", "_")
			.trim();
		if (safeName.isBlank()) {
			safeName = "inspeccion.zip";
		}
		if (!safeName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
			safeName += ".zip";
		}
		return safeName;
	}

	private String sanitizeFragment(String value) {
		if (value == null || value.isBlank()) {
			return "SIN_DATO";
		}
		return value.replaceAll("[^A-Za-z0-9_-]", "_");
	}

	private String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private String asString(Object value) {
		return value == null ? null : value.toString();
	}

	private String escapeCsv(String value) {
		String normalized = value == null ? "" : value.replace("\"", "\"\"");
		return "\"" + normalized + "\"";
	}

	private String formatValue(Object value) {
		if (value instanceof Timestamp timestamp) {
			return timestamp.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		}
		if (value instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		return Objects.toString(value, "");
	}

	private record TableMeta(List<String> columns, List<String> temporalColumns, List<String> primaryKeys) {
	}

	public record ExportedInspectionPackage(String fileName, String absolutePath) {
	}

	public record ImportInspectionResult(int processedFiles, List<String> paths) {
	}
}
