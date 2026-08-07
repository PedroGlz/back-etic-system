package com.etic.system.usuarios.infrastructure.out.persistence;

import com.etic.system.usuarios.domain.model.GrupoUsuario;
import com.etic.system.usuarios.domain.model.Usuario;
import com.etic.system.usuarios.domain.port.out.UsuarioPersistencePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
public class MySqlUsuarioPersistenceAdapter implements UsuarioPersistencePort {

	private static final String USER_SELECT = """
		SELECT u.Id_Usuario AS id, u.Id_Grupo AS groupId, g.Grupo AS groupName,
		       u.Usuario AS username, u.Nombre AS name, u.Email AS email,
		       u.Telefono AS phone, u.nivelCertificacion AS certificationLevel,
		       u.Estatus AS status
		FROM usuarios u
		LEFT JOIN grupos g ON g.Id_Grupo = u.Id_Grupo
		""";

	private final NamedParameterJdbcTemplate jdbc;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public MySqlUsuarioPersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Usuario> findAll() {
		return jdbc.query(USER_SELECT + " ORDER BY u.Nombre", Map.of(), this::mapUsuario);
	}

	@Override
	public Optional<Usuario> findById(String id) {
		String sql = USER_SELECT + " WHERE u.Id_Usuario = :id";
		return jdbc.query(sql, Map.of("id", id), this::mapUsuario).stream().findFirst();
	}

	@Override
	public Usuario create(Usuario usuario, String password, String userId) {
		String id = UUID.randomUUID().toString().toUpperCase();
		MapSqlParameterSource params = baseParams(usuario)
			.addValue("id", id)
			.addValue("password", passwordEncoder.encode(password))
			.addValue("status", "Activo")
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));

		jdbc.update("""
			INSERT INTO usuarios (
				Id_Usuario, Id_Grupo, Usuario, Nombre, Password, Email, Telefono,
				nivelCertificacion, Estatus, Creado_Por, Fecha_Creacion
			) VALUES (
				:id, :groupId, :username, :name, :password, :email, :phone,
				:certificationLevel, :status, :userId, :now
			)
			""", params);
		return findById(id).orElseThrow();
	}

	@Override
	public Usuario update(String id, Usuario usuario, String password, String userId) {
		MapSqlParameterSource params = baseParams(usuario)
			.addValue("id", id)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));

		String passwordAssignment = "";
		if (password != null) {
			passwordAssignment = "Password = :password,";
			params.addValue("password", passwordEncoder.encode(password));
		}

		jdbc.update("""
			UPDATE usuarios SET
				Id_Grupo = :groupId,
				Usuario = :username,
				Nombre = :name,
				%s
				Email = :email,
				Telefono = :phone,
				nivelCertificacion = :certificationLevel,
				Modificado_Por = :userId,
				Fecha_Mod = :now
			WHERE Id_Usuario = :id
			""".formatted(passwordAssignment), params);
		return findById(id).orElseThrow();
	}

	@Override
	public void updateStatus(String id, String status, String userId) {
		MapSqlParameterSource params = new MapSqlParameterSource("id", id)
			.addValue("status", status)
			.addValue("userId", userId)
			.addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE usuarios SET
				Estatus = :status,
				Modificado_Por = :userId,
				Fecha_Mod = :now
			WHERE Id_Usuario = :id
			""", params);
	}

	@Override
	public boolean existsByUsername(String username, String excludedId) {
		return exists("Usuario", username, excludedId);
	}

	@Override
	public boolean existsByEmail(String email, String excludedId) {
		return exists("Email", email, excludedId);
	}

	@Override
	public Optional<GrupoUsuario> findGroupById(String id) {
		String sql = "SELECT Id_Grupo AS id, Grupo AS name, Estatus AS status FROM grupos WHERE Id_Grupo = :id";
		return jdbc.query(sql, Map.of("id", id), this::mapGrupo).stream().findFirst();
	}

	@Override
	public List<GrupoUsuario> findActiveGroups() {
		String sql = """
			SELECT Id_Grupo AS id, Grupo AS name, Estatus AS status
			FROM grupos
			WHERE Estatus = 'Activo'
			ORDER BY Grupo
			""";
		return jdbc.query(sql, Map.of(), this::mapGrupo);
	}

	private boolean exists(String column, String value, String excludedId) {
		String excludedFilter = excludedId == null ? "" : " AND Id_Usuario <> :excludedId";
		MapSqlParameterSource params = new MapSqlParameterSource("value", value);
		if (excludedId != null) {
			params.addValue("excludedId", excludedId);
		}
		Integer count = jdbc.queryForObject(
			"SELECT COUNT(*) FROM usuarios WHERE " + column + " = :value" + excludedFilter,
			params,
			Integer.class
		);
		return count != null && count > 0;
	}

	private MapSqlParameterSource baseParams(Usuario usuario) {
		return new MapSqlParameterSource()
			.addValue("groupId", usuario.groupId())
			.addValue("username", usuario.username())
			.addValue("name", usuario.name())
			.addValue("email", usuario.email())
			.addValue("phone", usuario.phone())
			.addValue("certificationLevel", usuario.certificationLevel());
	}

	private Usuario mapUsuario(ResultSet rs, int rowNum) throws SQLException {
		return new Usuario(
			rs.getString("id"),
			rs.getString("groupId"),
			rs.getString("groupName"),
			rs.getString("username"),
			rs.getString("name"),
			rs.getString("email"),
			rs.getString("phone"),
			rs.getString("certificationLevel"),
			rs.getString("status")
		);
	}

	private GrupoUsuario mapGrupo(ResultSet rs, int rowNum) throws SQLException {
		return new GrupoUsuario(
			rs.getString("id"),
			rs.getString("name"),
			rs.getString("status")
		);
	}
}
