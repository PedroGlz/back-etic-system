package com.etic.system.licensing.distribution;

import com.etic.system.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DistributionService {
	private final NamedParameterJdbcTemplate licensing;
	private final NamedParameterJdbcTemplate etic;
	private final ApkStorage storage;

	public DistributionService(@Qualifier("licensingJdbc") NamedParameterJdbcTemplate licensing,
		@Qualifier("namedParameterJdbcTemplate") NamedParameterJdbcTemplate etic, ApkStorage storage) {
		this.licensing = licensing;
		this.etic = etic;
		this.storage = storage;
	}

	public AuthenticatedUser activeUser(HttpSession session) {
		var user=com.etic.system.auth.security.WebIdentity.current();
		return new AuthenticatedUser(user.id(),user.username(),user.firstName(),user.email(),null,null,null,null);
	}

	private boolean activeUserExists(String id) {
		Integer count = etic.queryForObject(
			"SELECT COUNT(*) FROM usuarios WHERE Id_Usuario=:id AND Estatus='Activo'",
			Map.of("id", id), Integer.class);
		return count != null && count > 0;
	}

	public List<Version> adminVersions() {
		return licensing.query(versionSql() + " ORDER BY v.Created_At DESC", Map.of(), (r, n) -> version(r));
	}

	public Version upload(String applicationId, String versionName, Long versionCode,
		String minimumAndroid, String releaseNotes, boolean mandatory, boolean published,
		MultipartFile file, String actorId) {
		if (versionName == null || versionName.isBlank() || versionName.length() > 80 ||
			(versionCode != null && versionCode <= 0))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Versión inválida");
		if (minimumAndroid != null && minimumAndroid.length() > 40)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Android mínimo inválido");
		if (releaseNotes != null && releaseNotes.length() > 10000)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notas demasiado largas");
		String code = licensing.query("SELECT Code FROM licensed_applications WHERE Id_Application=:id",
			Map.of("id", applicationId), (r, n) -> r.getString(1)).stream().findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aplicación no encontrada"));
		String id = UUID.randomUUID().toString().toUpperCase();
		if (versionCode != null) {
			Integer exists = licensing.queryForObject(
				"SELECT COUNT(*) FROM application_versions WHERE Id_Application=:app AND Version_Code=:code",
				Map.of("app", applicationId, "code", versionCode), Integer.class);
			if (exists != null && exists > 0)
				throw new ResponseStatusException(HttpStatus.CONFLICT, "VersionCode duplicado");
		}
		ApkStorage.StoredApk stored = versionCode == null
			? storage.store(code, id, file)
			: storage.store(code, versionCode.longValue(), file);
		try {
			licensing.update("INSERT INTO application_versions (Id_Version,Id_Application,Version_Name,Version_Code,Original_File_Name,Storage_File_Name,Sha256,File_Size,Minimum_Android,Release_Notes,Mandatory,Published,Created_At,Created_By) VALUES (:id,:app,:name,:code,:original,:storage,:sha,:size,:minimum,:notes,:mandatory,:published,:now,:actor)",
				new MapSqlParameterSource().addValue("id", id).addValue("app", applicationId)
					.addValue("name", versionName.trim()).addValue("code", versionCode)
					.addValue("original", stored.originalName()).addValue("storage", stored.path())
					.addValue("sha", stored.sha256()).addValue("size", stored.size())
					.addValue("minimum", minimumAndroid).addValue("notes", releaseNotes)
					.addValue("mandatory", mandatory).addValue("published", published)
					.addValue("now", LocalDateTime.now()).addValue("actor", actorId));
		} catch (RuntimeException e) {
			storage.discard(stored.path());
			throw e;
		}
		return adminVersion(id);
	}

	public Version upload(String applicationId, String versionName, long versionCode,
		String minimumAndroid, String releaseNotes, boolean mandatory, boolean published,
		MultipartFile file, String actorId) {
		return upload(applicationId, versionName, Long.valueOf(versionCode), minimumAndroid,
			releaseNotes, mandatory, published, file, actorId);
	}

	public Version publish(String id, boolean published, String actorId) {
		adminVersion(id);
		licensing.update("UPDATE application_versions SET Published=:published,Modified_At=:now,Modified_By=:actor WHERE Id_Version=:id",
			new MapSqlParameterSource().addValue("published", published).addValue("now", LocalDateTime.now())
				.addValue("actor", actorId).addValue("id", id));
		return adminVersion(id);
	}

	public Version adminVersion(String id) {
		return licensing.query(versionSql() + " WHERE v.Id_Version=:id", Map.of("id", id),
			(r, n) -> version(r)).stream().findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versión no encontrada"));
	}

	public Path adminDownload(String versionId) {
		adminVersion(versionId);
		return storage.resolve(versionPath(versionId));
	}

	private String versionPath(String versionId) {
		return licensing.query("SELECT Storage_File_Name FROM application_versions WHERE Id_Version=:id",
			Map.of("id", versionId), (r, n) -> r.getString(1)).stream().findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private String versionSql() {
		return "SELECT v.*,a.Name AS Application_Name FROM application_versions v JOIN licensed_applications a ON a.Id_Application=v.Id_Application";
	}

	private Version version(java.sql.ResultSet r) throws java.sql.SQLException {
		return new Version(r.getString("Id_Version"), r.getString("Id_Application"),
			r.getString("Application_Name"), r.getString("Version_Name"), r.getObject("Version_Code", Long.class),
			r.getString("Original_File_Name"), r.getString("Sha256"), r.getLong("File_Size"),
			r.getString("Minimum_Android"), r.getString("Release_Notes"),
			r.getBoolean("Mandatory"), r.getBoolean("Published"), r.getTimestamp("Created_At").toLocalDateTime());
	}

	public record Version(String id, String applicationId, String applicationName,
		String versionName, Long versionCode, String originalFileName, String sha256,
		long fileSize, String minimumAndroid, String releaseNotes, boolean mandatory,
		boolean published, LocalDateTime createdAt) {
		public Version(String id, String applicationId, String applicationName,
			String versionName, long versionCode, String originalFileName, String sha256,
			long fileSize, String minimumAndroid, String releaseNotes, boolean mandatory,
			boolean published, LocalDateTime createdAt) {
			this(id, applicationId, applicationName, versionName, Long.valueOf(versionCode),
				originalFileName, sha256, fileSize, minimumAndroid, releaseNotes, mandatory,
				published, createdAt);
		}
	}

	public List<Access> adminAccess() {
		return licensing.query(accessSql() + " ORDER BY x.Created_At DESC", Map.of(),
			(r, n) -> access(r));
	}

	public Access saveAccess(String id, String userId, String applicationId, String status,
		LocalDate from, LocalDate until, int maxDevices, String actorId) {
		if (userId == null || userId.isBlank() || applicationId == null || applicationId.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario y aplicación obligatorios");
		if (!activeUserExists(userId)) {
			if (id == null || "ACTIVE".equals(status))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario inexistente o inactivo");
			Integer exists = etic.queryForObject(
				"SELECT COUNT(*) FROM usuarios WHERE Id_Usuario=:id", Map.of("id", userId), Integer.class);
			if (exists == null || exists == 0)
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario inexistente");
		}
		if (!List.of("ACTIVE", "SUSPENDED", "REVOKED").contains(status) ||
			from == null || (until != null && until.isBefore(from)) || maxDevices < 1)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado o vigencia inválidos");
		Integer app = licensing.queryForObject(
			"SELECT COUNT(*) FROM licensed_applications WHERE Id_Application=:id",
			Map.of("id", applicationId), Integer.class);
		if (app == null || app == 0)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aplicación no encontrada");
		if (id == null) {
			id = UUID.randomUUID().toString().toUpperCase();
			licensing.update("INSERT INTO user_application_access (Id_Access,Id_Usuario,Id_Application,Status,Valid_From,Valid_Until,Max_Devices,Created_At,Created_By) VALUES (:id,:user,:app,:status,:from,:until,:maxDevices,:now,:actor)",
				new MapSqlParameterSource().addValue("id", id).addValue("user", userId)
					.addValue("app", applicationId).addValue("status", status).addValue("from", from)
					.addValue("until", until).addValue("maxDevices", maxDevices).addValue("now", LocalDateTime.now()).addValue("actor", actorId));
		} else {
			Access old = adminAccess(id);
			if (!old.userId().equals(userId) || !old.applicationId().equals(applicationId))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede cambiar la identidad del acceso");
			licensing.update("UPDATE user_application_access SET Status=:status,Valid_From=:from,Valid_Until=:until,Max_Devices=:maxDevices,Modified_At=:now,Modified_By=:actor WHERE Id_Access=:id",
				new MapSqlParameterSource().addValue("id", id).addValue("status", status)
					.addValue("from", from).addValue("until", until).addValue("maxDevices", maxDevices)
					.addValue("now", LocalDateTime.now()).addValue("actor", actorId));
		}
		return adminAccess(id);
	}

	public Access saveAccess(String id, String userId, String applicationId, String status,
		LocalDate from, LocalDate until, String actorId) {
		return saveAccess(id, userId, applicationId, status, from, until, 1, actorId);
	}

	private Access adminAccess(String id) {
		return licensing.query(accessSql() + " WHERE x.Id_Access=:id", Map.of("id", id),
			(r, n) -> access(r)).stream().findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Acceso no encontrado"));
	}

	private String accessSql() {
		return "SELECT x.*,a.Name AS Application_Name FROM user_application_access x JOIN licensed_applications a ON a.Id_Application=x.Id_Application";
	}

	private Access access(java.sql.ResultSet r) throws java.sql.SQLException {
		java.sql.Date until = r.getDate("Valid_Until");
		return new Access(r.getString("Id_Access"), r.getString("Id_Usuario"),
			r.getString("Id_Application"), r.getString("Application_Name"),
			r.getString("Status"), r.getDate("Valid_From").toLocalDate(),
			until == null ? null : until.toLocalDate(), r.getInt("Max_Devices"),
			r.getTimestamp("Created_At").toLocalDateTime());
	}

	public record Access(String id, String userId, String applicationId,
		String applicationName, String status, LocalDate validFrom, LocalDate validUntil,
		int maxDevices, LocalDateTime createdAt) {
		public Access(String id, String userId, String applicationId, String applicationName,
			String status, LocalDate validFrom, LocalDate validUntil, LocalDateTime createdAt) {
			this(id, userId, applicationId, applicationName, status, validFrom, validUntil, 1, createdAt);
		}
	}

	public List<PortalApplication> portalApps(AuthenticatedUser user) {
		return licensing.query(
			"SELECT a.Id_Application,a.Code,a.Name FROM licensed_applications a JOIN user_application_access x ON x.Id_Application=a.Id_Application WHERE x.Id_Usuario=:user AND x.Status='ACTIVE' AND x.Valid_From<=CURRENT_DATE AND (x.Valid_Until IS NULL OR x.Valid_Until>=CURRENT_DATE) AND a.Status='ACTIVE' ORDER BY a.Name",
			Map.of("user", user.id()), (r, n) -> new PortalApplication(
				r.getString("Id_Application"), r.getString("Code"), r.getString("Name"),
				latestVersion(r.getString("Id_Application"))));
	}

	public PortalApplication portalApp(AuthenticatedUser user, String applicationId) {
		return portalApps(user).stream().filter(a -> a.id().equals(applicationId)).findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aplicación no disponible"));
	}

	public List<Version> portalVersions(AuthenticatedUser user, String applicationId) {
		portalApp(user, applicationId);
		return licensing.query(versionSql() +
			" WHERE v.Id_Application=:id AND v.Published=TRUE ORDER BY (v.Version_Code IS NULL) DESC, CASE WHEN v.Version_Code IS NULL THEN v.Created_At END DESC, v.Version_Code DESC",
			Map.of("id", applicationId), (r, n) -> version(r));
	}

	public Version latestVersion(String applicationId) {
		return licensing.query(versionSql() +
			" WHERE v.Id_Application=:id AND v.Published=TRUE ORDER BY (v.Version_Code IS NULL) DESC, CASE WHEN v.Version_Code IS NULL THEN v.Created_At END DESC, v.Version_Code DESC LIMIT 1",
			Map.of("id", applicationId), (r, n) -> version(r)).stream().findFirst().orElse(null);
	}

	public Path portalDownload(AuthenticatedUser user, String versionId) {
		Version version = licensing.query(versionSql() +
			" WHERE v.Id_Version=:id AND v.Published=TRUE",
			Map.of("id", versionId), (r, n) -> version(r)).stream().findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versión no disponible"));
		portalApp(user, version.applicationId());
		return storage.resolve(versionPath(versionId));
	}

	public record PortalApplication(String id, String code, String name, Version latestVersion) {}
}
