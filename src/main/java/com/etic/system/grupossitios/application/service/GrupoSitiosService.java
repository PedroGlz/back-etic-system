package com.etic.system.grupossitios.application.service;

import com.etic.system.grupossitios.domain.model.GrupoSitios;
import com.etic.system.grupossitios.infrastructure.in.rest.request.GrupoSitiosRequest;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GrupoSitiosService {

	private static final String ACTIVE = "Activo";
	private static final String INACTIVE = "Inactivo";

	private static final String SELECT = """
		SELECT gs.Id_Grupo_Sitios AS id, gs.Id_Cliente AS clientId, c.Razon_Social AS clientName,
		       gs.Grupo AS name, gs.Estatus AS status
		FROM grupos_sitios gs
		LEFT JOIN clientes c ON c.Id_Cliente = gs.Id_Cliente
		""";

	private final NamedParameterJdbcTemplate jdbc;

	public GrupoSitiosService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<GrupoSitios> findAll() {
		return jdbc.query(SELECT + " ORDER BY c.Razon_Social, gs.Grupo", Map.of(), this::mapRecord);
	}

	public GrupoSitios findById(String id) {
		return jdbc.query(SELECT + " WHERE gs.Id_Grupo_Sitios = :id", Map.of("id", id), this::mapRecord)
			.stream()
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Grupo de sitios no encontrado"));
	}

	@Transactional
	public GrupoSitios create(GrupoSitiosRequest request, String userId) {
		validate(request.clientId());
		String id = UUID.randomUUID().toString().toUpperCase();
		MapSqlParameterSource params = params(request, userId).addValue("id", id).addValue("status", ACTIVE);
		jdbc.update("""
			INSERT INTO grupos_sitios (
				Id_Grupo_Sitios, Id_Cliente, Grupo, Estatus, Creado_Por, Fecha_Creacion
			) VALUES (
				:id, :clientId, :name, :status, :userId, :now
			)
			""", params);
		return findById(id);
	}

	@Transactional
	public GrupoSitios update(String id, GrupoSitiosRequest request, String userId) {
		findById(id);
		validate(request.clientId());
		MapSqlParameterSource params = params(request, userId).addValue("id", id);
		jdbc.update("""
			UPDATE grupos_sitios SET
				Id_Cliente = :clientId,
				Grupo = :name,
				Modificado_Por = :userId,
				Fecha_Mod = :now
			WHERE Id_Grupo_Sitios = :id
			""", params);
		return findById(id);
	}

	@Transactional
	public GrupoSitios changeStatus(String id, String status, String userId) {
		findById(id);
		validateStatus(status);
		updateStatus(id, status, userId);
		return findById(id);
	}

	@Transactional
	public void deactivate(String id, String userId) {
		findById(id);
		updateStatus(id, INACTIVE, userId);
	}

	private void validate(String clientId) {
		Integer count = jdbc.queryForObject(
			"SELECT COUNT(*) FROM clientes WHERE Id_Cliente = :clientId AND Estatus = :status",
			Map.of("clientId", clientId, "status", ACTIVE),
			Integer.class
		);
		if (count == null || count == 0) {
			throw new BusinessValidationException("El cliente seleccionado no existe o no está activo");
		}
	}

	private void validateStatus(String status) {
		if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
			throw new BusinessValidationException("Estatus de grupo de sitios no permitido");
		}
	}

	private void updateStatus(String id, String status, String userId) {
		MapSqlParameterSource params = new MapSqlParameterSource("id", id)
			.addValue("status", status)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE grupos_sitios SET Estatus = :status, Modificado_Por = :userId, Fecha_Mod = :now
			WHERE Id_Grupo_Sitios = :id
			""", params);
	}

	private MapSqlParameterSource params(GrupoSitiosRequest request, String userId) {
		return new MapSqlParameterSource()
			.addValue("clientId", request.clientId())
			.addValue("name", request.name())
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
	}

	private GrupoSitios mapRecord(ResultSet rs, int rowNum) throws SQLException {
		return new GrupoSitios(
			rs.getString("id"),
			rs.getString("clientId"),
			rs.getString("clientName"),
			rs.getString("name"),
			rs.getString("status")
		);
	}
}
