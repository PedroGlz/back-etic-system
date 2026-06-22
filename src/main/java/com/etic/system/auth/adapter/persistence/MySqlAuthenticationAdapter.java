package com.etic.system.auth.adapter.persistence;

import com.etic.system.auth.application.port.AuthenticationPort;
import com.etic.system.auth.domain.AuthenticatedUser;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MySqlAuthenticationAdapter implements AuthenticationPort {

	private final NamedParameterJdbcTemplate jdbc;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public MySqlAuthenticationAdapter(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<AuthenticatedUser> authenticate(String username, String password) {
		String sql = """
			SELECT u.Id_Usuario AS id, u.Usuario AS username, u.Nombre AS name,
			       u.Email AS email, u.Id_Grupo AS groupId, g.Grupo AS groupName,
			       u.Titulo AS title, u.nivelCertificacion AS certificationLevel,
			       u.Password AS passwordHash
			FROM usuarios u
			LEFT JOIN grupos g ON g.Id_Grupo = u.Id_Grupo
			WHERE u.Usuario = :username AND u.Estatus = 'Activo'
			""";
		List<UserRow> users = jdbc.query(sql, Map.of("username", username), (rs, rowNum) -> new UserRow(
			rs.getString("id"), rs.getString("username"), rs.getString("name"), rs.getString("email"),
			rs.getString("groupId"), rs.getString("groupName"), rs.getString("title"),
			rs.getString("certificationLevel"), rs.getString("passwordHash")));

		if (users.isEmpty() || users.getFirst().passwordHash() == null
			|| !passwordEncoder.matches(password, users.getFirst().passwordHash())) {
			return Optional.empty();
		}

		UserRow user = users.getFirst();
		jdbc.update("UPDATE usuarios SET Ultimo_login = :lastLogin WHERE Id_Usuario = :id",
			Map.of("lastLogin", Timestamp.valueOf(LocalDateTime.now()), "id", user.id()));
		return Optional.of(new AuthenticatedUser(user.id(), user.username(), user.name(), user.email(),
			user.groupId(), user.groupName(), user.title(), user.certificationLevel()));
	}

	private record UserRow(String id, String username, String name, String email, String groupId,
		String groupName, String title, String certificationLevel, String passwordHash) {
	}
}
