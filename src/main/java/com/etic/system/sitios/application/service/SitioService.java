package com.etic.system.sitios.application.service;

import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import com.etic.system.sitios.domain.model.Sitio;
import com.etic.system.sitios.domain.model.SitioContacto;
import com.etic.system.sitios.infrastructure.in.rest.request.SitioContactoRequest;
import com.etic.system.sitios.infrastructure.in.rest.request.SitioRequest;
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
public class SitioService {

	private static final String ACTIVE = "Activo";
	private static final String INACTIVE = "Inactivo";

	private static final String SELECT = """
		SELECT s.Id_Sitio AS id, s.Id_Cliente AS clientId, c.Razon_Social AS clientName,
		       s.Id_Grupo_Sitios AS siteGroupId, gs.Grupo AS siteGroupName,
		       s.Sitio AS name, s.Desc_Sitio AS description, s.Direccion AS address,
		       s.Colonia AS neighborhood, s.Estado AS state, s.Municipio AS municipality,
		       s.Estatus AS status
		FROM sitios s
		LEFT JOIN clientes c ON c.Id_Cliente = s.Id_Cliente
		LEFT JOIN grupos_sitios gs ON gs.Id_Grupo_Sitios = s.Id_Grupo_Sitios
		""";

	private final NamedParameterJdbcTemplate jdbc;

	public SitioService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Sitio> findAll() {
		return jdbc.query(SELECT + " ORDER BY c.Razon_Social, s.Sitio", Map.of(), this::mapRecord);
	}

	public Sitio findById(String id) {
		return jdbc.query(SELECT + " WHERE s.Id_Sitio = :id", Map.of("id", id), this::mapRecord)
			.stream()
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Sitio no encontrado"));
	}

	@Transactional
	public Sitio create(SitioRequest request, String userId) {
		validate(request);
		String id = UUID.randomUUID().toString().toUpperCase();
		MapSqlParameterSource params = params(request, userId).addValue("id", id).addValue("status", ACTIVE);
		jdbc.update("""
			INSERT INTO sitios (
				Id_Sitio, Id_Cliente, Id_Grupo_Sitios, Sitio, Desc_Sitio, Direccion, Colonia,
				Estado, Municipio, Estatus, Creado_Por, Fecha_Creacion
			) VALUES (
				:id, :clientId, :siteGroupId, :name, :description, :address, :neighborhood,
				:state, :municipality, :status, :userId, :now
			)
			""", params);
		saveContacts(id, request);
		return findById(id);
	}

	@Transactional
	public Sitio update(String id, SitioRequest request, String userId) {
		findById(id);
		validate(request);
		MapSqlParameterSource params = params(request, userId).addValue("id", id);
		jdbc.update("""
			UPDATE sitios SET
				Id_Cliente = :clientId,
				Id_Grupo_Sitios = :siteGroupId,
				Sitio = :name,
				Desc_Sitio = :description,
				Direccion = :address,
				Colonia = :neighborhood,
				Estado = :state,
				Municipio = :municipality,
				Modificado_Por = :userId,
				Fecha_Mod = :now
			WHERE Id_Sitio = :id
			""", params);
		saveContacts(id, request);
		return findById(id);
	}

	@Transactional
	public Sitio changeStatus(String id, String status, String userId) {
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

	private void validate(SitioRequest request) {
		Integer clientCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM clientes WHERE Id_Cliente = :clientId AND Estatus = :status",
			Map.of("clientId", request.clientId(), "status", ACTIVE),
			Integer.class
		);
		if (clientCount == null || clientCount == 0) {
			throw new BusinessValidationException("El cliente seleccionado no existe o no está activo");
		}

		if (request.siteGroupId() == null || request.siteGroupId().isBlank()) {
			return;
		}

		Integer groupCount = jdbc.queryForObject(
			"""
			SELECT COUNT(*) FROM grupos_sitios
			WHERE Id_Grupo_Sitios = :siteGroupId AND Id_Cliente = :clientId AND Estatus = :status
			""",
			Map.of("siteGroupId", request.siteGroupId(), "clientId", request.clientId(), "status", ACTIVE),
			Integer.class
		);
		if (groupCount == null || groupCount == 0) {
			throw new BusinessValidationException("El grupo de sitios seleccionado no pertenece al cliente o no está activo");
		}
	}

	private void validateStatus(String status) {
		if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
			throw new BusinessValidationException("Estatus de sitio no permitido");
		}
	}

	private void updateStatus(String id, String status, String userId) {
		MapSqlParameterSource params = new MapSqlParameterSource("id", id)
			.addValue("status", status)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE sitios SET Estatus = :status, Modificado_Por = :userId, Fecha_Mod = :now
			WHERE Id_Sitio = :id
			""", params);
	}

	private MapSqlParameterSource params(SitioRequest request, String userId) {
		return new MapSqlParameterSource()
			.addValue("clientId", request.clientId())
			.addValue("siteGroupId", blankToNull(request.siteGroupId()))
			.addValue("name", request.name())
			.addValue("description", blankToNull(request.description()))
			.addValue("address", blankToNull(request.address()))
			.addValue("neighborhood", blankToNull(request.neighborhood()))
			.addValue("state", blankToNull(request.state()))
			.addValue("municipality", blankToNull(request.municipality()))
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private List<SitioContactoRequest> requestContacts(SitioRequest request) {
		if (request.contacts() == null) {
			return List.of();
		}
		return request.contacts().stream()
			.filter(contact -> contact != null && (blankToNull(contact.name()) != null || blankToNull(contact.role()) != null))
			.toList();
	}

	private void saveContacts(String siteId, SitioRequest request) {
		List<SitioContactoRequest> contacts = requestContacts(request);
		jdbc.update("DELETE FROM sitio_contactos WHERE Id_Sitio = :siteId", Map.of("siteId", siteId));
		for (int index = 0; index < contacts.size(); index++) {
			SitioContactoRequest contact = contacts.get(index);
			String id = contact.id() == null || contact.id().isBlank()
				? UUID.randomUUID().toString().toUpperCase()
				: contact.id().toUpperCase();
			MapSqlParameterSource contactParams = new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("siteId", siteId)
				.addValue("name", blankToNull(contact.name()))
				.addValue("role", blankToNull(contact.role()))
				.addValue("status", ACTIVE)
				.addValue("order", index + 1);
			jdbc.update("""
				INSERT INTO sitio_contactos (Id_Sitio_Contacto, Id_Sitio, Nombre, Puesto, Estatus, Orden)
				VALUES (:id, :siteId, :name, :role, :status, :order)
				""", contactParams);
		}
	}

	private List<SitioContacto> findContacts(String siteId) {
		return jdbc.query("""
			SELECT Id_Sitio_Contacto, Nombre, Puesto, Estatus, Orden
			FROM sitio_contactos WHERE Id_Sitio = :siteId AND Estatus = :status
			ORDER BY Orden, Id_Sitio_Contacto
			""", Map.of("siteId", siteId, "status", ACTIVE), (rs, rowNum) -> new SitioContacto(
			rs.getString("Id_Sitio_Contacto"), rs.getString("Nombre"), rs.getString("Puesto"),
			rs.getString("Estatus"), rs.getInt("Orden")
		));
	}

	private Sitio mapRecord(ResultSet rs, int rowNum) throws SQLException {
		return new Sitio(
			rs.getString("id"),
			rs.getString("clientId"),
			rs.getString("clientName"),
			rs.getString("siteGroupId"),
			rs.getString("siteGroupName"),
			rs.getString("name"),
			rs.getString("description"),
			rs.getString("address"),
			rs.getString("neighborhood"),
			rs.getString("state"),
			rs.getString("municipality"),
			rs.getString("status"),
			findContacts(rs.getString("id"))
		);
	}
}
