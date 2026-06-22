package com.etic.system.inspecciones.infrastructure.out.persistence;

import com.etic.system.inspecciones.application.command.UpsertInspectionCommand;
import com.etic.system.inspecciones.application.port.out.InspectionPersistencePort;
import com.etic.system.inspecciones.domain.model.InspectionLocation;
import com.etic.system.inspecciones.domain.model.InspectionSession;
import com.etic.system.inspecciones.domain.model.InspectionStatusIds;
import com.etic.system.inspecciones.domain.model.InspectionSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MySqlInspectionPersistenceAdapter implements InspectionPersistencePort {

	private static final String ACTIVE = "Activo";
	private static final String INACTIVE = "Inactivo";

	private final NamedParameterJdbcTemplate jdbc;

	public MySqlInspectionPersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<InspectionSummary> findAll() {
		return jdbc.query(baseSelect() + " WHERE i.Estatus = :status ORDER BY i.No_Inspeccion DESC",
			Map.of("status", ACTIVE),
			(rs, rowNum) -> mapSummary(rs)
		);
	}

	@Override
	public Optional<InspectionSummary> findById(String inspectionId) {
		return jdbc.query(baseSelect() + " WHERE i.Id_Inspeccion = :inspectionId",
			Map.of("inspectionId", inspectionId),
			(rs, rowNum) -> mapSummary(rs)
		).stream().findFirst();
	}

	@Override
	public Optional<InspectionSession> findSessionById(String inspectionId) {
		String sql = """
			SELECT i.Id_Inspeccion AS id, i.No_Inspeccion AS inspectionNumber,
			       i.Id_Cliente AS clientId, c.Razon_Social AS clientName,
			       i.Id_Sitio AS siteId, s.Sitio AS siteName,
			       i.Id_Status_Inspeccion AS statusId, ei.Status_Inspeccion AS statusName
			FROM inspecciones i
			LEFT JOIN clientes c ON c.Id_Cliente = i.Id_Cliente
			LEFT JOIN sitios s ON s.Id_Sitio = i.Id_Sitio
			LEFT JOIN estatus_inspeccion ei ON ei.Id_Status_Inspeccion = i.Id_Status_Inspeccion
			WHERE i.Id_Inspeccion = :inspectionId AND i.Estatus = :status
			""";
		return jdbc.query(sql, Map.of("inspectionId", inspectionId, "status", ACTIVE), (rs, rowNum) ->
			new InspectionSession(
				rs.getString("id"),
				(Integer) rs.getObject("inspectionNumber"),
				rs.getString("clientId"),
				rs.getString("clientName"),
				rs.getString("siteId"),
				rs.getString("siteName"),
				rs.getString("statusId"),
				rs.getString("statusName")
			)
		).stream().findFirst();
	}

	@Override
	public boolean hasOpenInspectionOnSite(String siteId, String excludedInspectionId) {
		String sql = """
			SELECT COUNT(*)
			FROM inspecciones
			WHERE Id_Sitio = :siteId
			  AND Id_Status_Inspeccion = :statusId
			  AND Estatus = :recordStatus
			  AND (:excludedInspectionId IS NULL OR Id_Inspeccion <> :excludedInspectionId)
			""";
		Integer count = jdbc.queryForObject(sql, Map.of(
			"siteId", siteId,
			"statusId", InspectionStatusIds.IN_PROGRESS,
			"recordStatus", ACTIVE,
			"excludedInspectionId", excludedInspectionId
		), Integer.class);
		return count != null && count > 0;
	}

	@Override
	public Integer findMaxInspectionNumber() {
		return jdbc.queryForObject("SELECT MAX(No_Inspeccion) FROM inspecciones", Map.of(), Integer.class);
	}

	@Override
	public List<InspectionLocation> findActiveLocationsBySite(String siteId) {
		String sql = "SELECT Id_Ubicacion AS id, Estatus AS status FROM ubicaciones WHERE Id_Sitio = :siteId AND Estatus = :status";
		return jdbc.query(sql, Map.of("siteId", siteId, "status", ACTIVE), (rs, rowNum) ->
			new InspectionLocation(rs.getString("id"), rs.getString("status"))
		);
	}

	@Override
	public String createInspection(
		UpsertInspectionCommand command,
		String statusId,
		int inspectionNumber,
		Integer previousInspectionNumber,
		int daysCount,
		String photosRoute,
		String userId
	) {
		String inspectionId = UUID.randomUUID().toString().toUpperCase();
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("inspectionId", inspectionId)
			.addValue("siteId", command.siteId())
			.addValue("clientId", command.clientId())
			.addValue("siteGroupId", command.siteGroupId())
			.addValue("statusId", statusId)
			.addValue("startDate", timestamp(command.startDate()))
			.addValue("endDate", timestamp(command.endDate()))
			.addValue("photosRoute", photosRoute)
			.addValue("daysCount", daysCount)
			.addValue("temperatureUnit", normalizeTemperature(command.temperatureUnit()))
			.addValue("inspectionNumber", inspectionNumber)
			.addValue("previousInspectionNumber", previousInspectionNumber)
			.addValue("status", ACTIVE)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));

		jdbc.update("""
			INSERT INTO inspecciones (
				Id_Inspeccion, Id_Sitio, Id_Cliente, Id_Grupo_Sitios, Id_Status_Inspeccion,
				Fecha_Inicio, Fecha_Fin, Fotos_Ruta, No_Dias, Unidad_Temp, No_Inspeccion,
				No_Inspeccion_Ant, Estatus, Creado_Por, Fecha_Creacion
			) VALUES (
				:inspectionId, :siteId, :clientId, :siteGroupId, :statusId,
				:startDate, :endDate, :photosRoute, :daysCount, :temperatureUnit, :inspectionNumber,
				:previousInspectionNumber, :status, :userId, :now
			)
			""", params);
		return inspectionId;
	}

	@Override
	public void createInspectionDetails(String inspectionId, String siteId, List<InspectionLocation> locations, String userId) {
		String sql = """
			INSERT INTO inspecciones_det (
				Id_Inspeccion_Det, Id_Inspeccion, Id_Ubicacion, Id_Sitio, Id_Status_Inspeccion_Det,
				Notas_Inspeccion, Id_Estatus_Color_Text, Estatus, Creado_Por, Fecha_Creacion
			) VALUES (
				:detailId, :inspectionId, :locationId, :siteId, :detailStatusId,
				:notes, :colorTextId, :status, :userId, :now
			)
			""";
		MapSqlParameterSource[] batch = locations.stream()
			.map(location -> new MapSqlParameterSource()
				.addValue("detailId", UUID.randomUUID().toString().toUpperCase())
				.addValue("inspectionId", inspectionId)
				.addValue("locationId", location.id())
				.addValue("siteId", siteId)
				.addValue("detailStatusId", InspectionStatusIds.DETAIL_INITIAL)
				.addValue("notes", "")
				.addValue("colorTextId", "1")
				.addValue("status", location.status())
				.addValue("userId", userId)
				.addValue("now", Timestamp.valueOf(LocalDateTime.now())))
			.toArray(MapSqlParameterSource[]::new);
		jdbc.batchUpdate(sql, batch);
	}

	@Override
	public void updateInspection(
		String inspectionId,
		UpsertInspectionCommand command,
		String statusId,
		int daysCount,
		String userId
	) {
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("inspectionId", inspectionId)
			.addValue("clientId", command.clientId())
			.addValue("siteGroupId", command.siteGroupId())
			.addValue("siteId", command.siteId())
			.addValue("statusId", statusId)
			.addValue("startDate", timestamp(command.startDate()))
			.addValue("endDate", timestamp(command.endDate()))
			.addValue("daysCount", daysCount)
			.addValue("temperatureUnit", normalizeTemperature(command.temperatureUnit()))
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));

		jdbc.update("""
			UPDATE inspecciones
			SET Id_Cliente = :clientId,
			    Id_Grupo_Sitios = :siteGroupId,
			    Id_Sitio = :siteId,
			    Id_Status_Inspeccion = :statusId,
			    Fecha_Inicio = :startDate,
			    Fecha_Fin = :endDate,
			    No_Dias = :daysCount,
			    Unidad_Temp = :temperatureUnit,
			    Modificado_Por = :userId,
			    Fecha_Mod = :now
			WHERE Id_Inspeccion = :inspectionId
			""", params);
	}

	@Override
	public void updateInspectionStatus(String inspectionId, String statusId, LocalDateTime endDate, int daysCount, String userId) {
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("inspectionId", inspectionId)
			.addValue("statusId", statusId)
			.addValue("endDate", timestamp(endDate))
			.addValue("daysCount", daysCount)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE inspecciones
			SET Id_Status_Inspeccion = :statusId,
			    Fecha_Fin = :endDate,
			    No_Dias = :daysCount,
			    Modificado_Por = :userId,
			    Fecha_Mod = :now
			WHERE Id_Inspeccion = :inspectionId
			""", params);
	}

	@Override
	public void deactivateInspection(String inspectionId, String userId) {
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("inspectionId", inspectionId)
			.addValue("status", INACTIVE)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE inspecciones_det
			SET Estatus = :status,
			    Modificado_Por = :userId,
			    Fecha_Mod = :now
			WHERE Id_Inspeccion = :inspectionId
			""", params);
		jdbc.update("""
			UPDATE inspecciones
			SET Estatus = :status,
			    Modificado_Por = :userId,
			    Fecha_Mod = :now
			WHERE Id_Inspeccion = :inspectionId
			""", params);
	}

	private String baseSelect() {
		return """
			SELECT i.Id_Inspeccion AS id,
			       i.No_Inspeccion AS inspectionNumber,
			       i.No_Inspeccion_Ant AS previousInspectionNumber,
			       i.Id_Cliente AS clientId,
			       c.Razon_Social AS clientName,
			       i.Id_Grupo_Sitios AS siteGroupId,
			       gs.Grupo AS siteGroupName,
			       i.Id_Sitio AS siteId,
			       s.Sitio AS siteName,
			       i.Id_Status_Inspeccion AS statusId,
			       ei.Status_Inspeccion AS statusName,
			       CASE
			           WHEN i.Fecha_Inicio = '0000-00-00 00:00:00' THEN NULL
			           ELSE i.Fecha_Inicio
			       END AS startDate,
			       CASE
			           WHEN i.Fecha_Fin = '0000-00-00 00:00:00' THEN NULL
			           ELSE i.Fecha_Fin
			       END AS endDate,
			       i.No_Dias AS daysCount,
			       i.Unidad_Temp AS temperatureUnit,
			       i.Fotos_Ruta AS photosRoute,
			       COALESCE(det.detailCount, 0) AS detailCount,
			       CASE WHEN latest.latestInspectionNumber = i.No_Inspeccion THEN true ELSE false END AS latestForSite,
			       CASE WHEN latest.latestInspectionNumber = i.No_Inspeccion AND i.Id_Status_Inspeccion <> '73F27007-76B3-11D3-82BF-00104BC75DC2' THEN true ELSE false END AS editable,
			       CASE WHEN latest.latestInspectionNumber = i.No_Inspeccion AND i.Id_Status_Inspeccion <> '73F27007-76B3-11D3-82BF-00104BC75DC2' THEN true ELSE false END AS deletable,
			       CASE WHEN latest.latestInspectionNumber = i.No_Inspeccion AND i.Id_Status_Inspeccion <> '73F27007-76B3-11D3-82BF-00104BC75DC2' THEN true ELSE false END AS exportable,
			       CASE WHEN latest.latestInspectionNumber = i.No_Inspeccion THEN true ELSE false END AS uploadable,
			       CASE WHEN i.Estatus = 'Activo' THEN true ELSE false END AS openable
			FROM inspecciones i
			LEFT JOIN clientes c ON c.Id_Cliente = i.Id_Cliente
			LEFT JOIN grupos_sitios gs ON gs.Id_Grupo_Sitios = i.Id_Grupo_Sitios
			LEFT JOIN sitios s ON s.Id_Sitio = i.Id_Sitio
			LEFT JOIN estatus_inspeccion ei ON ei.Id_Status_Inspeccion = i.Id_Status_Inspeccion
			LEFT JOIN (
				SELECT Id_Inspeccion, COUNT(*) AS detailCount
				FROM inspecciones_det
				WHERE Estatus = 'Activo'
				GROUP BY Id_Inspeccion
			) det ON det.Id_Inspeccion = i.Id_Inspeccion
			LEFT JOIN (
				SELECT Id_Sitio, MAX(No_Inspeccion) AS latestInspectionNumber
				FROM inspecciones
				WHERE Estatus = 'Activo'
				GROUP BY Id_Sitio
			) latest ON latest.Id_Sitio = i.Id_Sitio
			""";
	}

	private InspectionSummary mapSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new InspectionSummary(
			rs.getString("id"),
			asInteger(rs.getObject("inspectionNumber")),
			asInteger(rs.getObject("previousInspectionNumber")),
			rs.getString("clientId"),
			rs.getString("clientName"),
			rs.getString("siteGroupId"),
			rs.getString("siteGroupName"),
			rs.getString("siteId"),
			rs.getString("siteName"),
			rs.getString("statusId"),
			rs.getString("statusName"),
			toLocalDateTime(rs.getTimestamp("startDate")),
			toLocalDateTime(rs.getTimestamp("endDate")),
			asInteger(rs.getObject("daysCount")),
			asInteger(rs.getObject("detailCount")),
			rs.getString("temperatureUnit"),
			rs.getString("photosRoute"),
			rs.getBoolean("latestForSite"),
			rs.getBoolean("editable"),
			rs.getBoolean("deletable"),
			rs.getBoolean("exportable"),
			rs.getBoolean("uploadable"),
			rs.getBoolean("openable")
		);
	}

	private LocalDateTime toLocalDateTime(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private Integer asInteger(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}

	private Timestamp timestamp(LocalDateTime value) {
		return value == null ? null : Timestamp.valueOf(value);
	}

	private String normalizeTemperature(String value) {
		return value == null || value.isBlank() ? "C" : value.trim().toUpperCase();
	}
}
