package com.etic.system.clientes.infrastructure.out.persistence;

import com.etic.system.clientes.domain.model.Cliente;
import com.etic.system.clientes.domain.port.out.ClientePersistencePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MySqlClientePersistenceAdapter implements ClientePersistencePort {

	private static final String SELECT = """
		SELECT Id_Cliente AS id, Razon_Social AS businessName, Nombre_Comercial AS commercialName,
		       RFC AS rfc, Estatus AS status
		FROM clientes
		""";

	private final NamedParameterJdbcTemplate jdbc;

	public MySqlClientePersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Cliente> findAll() {
		return jdbc.query(SELECT + " ORDER BY Razon_Social", Map.of(), this::mapCliente);
	}

	@Override
	public Optional<Cliente> findById(String id) {
		return jdbc.query(SELECT + " WHERE Id_Cliente = :id", Map.of("id", id), this::mapCliente).stream().findFirst();
	}

	@Override
	public Cliente create(Cliente cliente, String userId) {
		String id = UUID.randomUUID().toString().toUpperCase();
		MapSqlParameterSource params = params(cliente)
			.addValue("id", id)
			.addValue("status", "Activo")
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			INSERT INTO clientes (
				Id_Cliente, Razon_Social, Nombre_Comercial, RFC, Estatus, Creado_Por, Fecha_Creacion
			) VALUES (
				:id, :businessName, :commercialName, :rfc, :status, :userId, :now
			)
			""", params);
		return findById(id).orElseThrow();
	}

	@Override
	public Cliente update(String id, Cliente cliente, String userId) {
		MapSqlParameterSource params = params(cliente)
			.addValue("id", id)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE clientes SET
				Razon_Social = :businessName,
				Nombre_Comercial = :commercialName,
				RFC = :rfc,
				Modificado_Por = :userId,
				Fecha_Mod = :now
			WHERE Id_Cliente = :id
			""", params);
		return findById(id).orElseThrow();
	}

	@Override
	public void updateStatus(String id, String status, String userId) {
		MapSqlParameterSource params = new MapSqlParameterSource("id", id)
			.addValue("status", status)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE clientes SET Estatus = :status, Modificado_Por = :userId, Fecha_Mod = :now
			WHERE Id_Cliente = :id
			""", params);
	}

	@Override
	public boolean existsByRfc(String rfc, String excludedId) {
		String excludedFilter = excludedId == null ? "" : " AND Id_Cliente <> :excludedId";
		MapSqlParameterSource params = new MapSqlParameterSource("rfc", rfc);
		if (excludedId != null) {
			params.addValue("excludedId", excludedId);
		}
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE RFC = :rfc" + excludedFilter, params, Integer.class);
		return count != null && count > 0;
	}

	private MapSqlParameterSource params(Cliente cliente) {
		return new MapSqlParameterSource()
			.addValue("businessName", cliente.businessName())
			.addValue("commercialName", cliente.commercialName())
			.addValue("rfc", cliente.rfc());
	}

	private Cliente mapCliente(ResultSet rs, int rowNum) throws SQLException {
		return new Cliente(
			rs.getString("id"),
			rs.getString("businessName"),
			rs.getString("commercialName"),
			rs.getString("rfc"),
			rs.getString("status")
		);
	}
}
