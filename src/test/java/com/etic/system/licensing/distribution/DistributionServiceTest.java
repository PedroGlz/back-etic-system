package com.etic.system.licensing.distribution;

import com.etic.system.auth.domain.AuthenticatedUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DistributionServiceTest {
	private NamedParameterJdbcTemplate licensing;
	private NamedParameterJdbcTemplate etic;
	private DistributionService service;
	private ApkStorage storage;
	private final AuthenticatedUser user = new AuthenticatedUser("U1", "user", "User", null, null, "Usuarios", null, null);

	@BeforeEach void setUp() {
		licensing = mock(NamedParameterJdbcTemplate.class);
		etic = mock(NamedParameterJdbcTemplate.class);
		storage = mock(ApkStorage.class);
		service = new DistributionService(licensing, etic, storage);
	}

	@Test void requiresSessionAndActiveEticUser() {
		assertThrows(ResponseStatusException.class, () -> service.activeUser(new MockHttpSession()));
		verifyNoInteractions(licensing, etic);
		MockHttpSession session = new MockHttpSession();
		session.setAttribute("authenticatedUser", user);
		when(etic.queryForObject(contains("FROM usuarios"), any(Map.class), eq(Integer.class))).thenReturn(0, 1);
		assertThrows(ResponseStatusException.class, () -> service.activeUser(session));
		assertEquals(user, service.activeUser(session));
		verifyNoInteractions(licensing);
	}

	@Test void noAccessCannotSeeOrDownloadApplication() {
		when(licensing.query(contains("user_application_access"), any(Map.class),
			any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(List.of());
		assertThrows(ResponseStatusException.class, () -> service.portalApp(user, "A1"));
		verify(licensing).query(argThat(sql -> sql.contains("x.Status='ACTIVE'") &&
			sql.contains("x.Valid_From<=CURRENT_DATE") && sql.contains("x.Valid_Until>=CURRENT_DATE") &&
			sql.contains("a.Status='ACTIVE'")), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
	}

	@Test void unpublishedVersionCannotBeDownloaded() {
		assertThrows(ResponseStatusException.class, () -> service.portalDownload(user, "V1"));
		verify(licensing).query(argThat(sql -> sql.contains("v.Published=TRUE")),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
	}

	@Test void accessRequiresActiveEticUserBeforeLicensingWrite() {
		when(etic.queryForObject(contains("FROM usuarios"), any(Map.class), eq(Integer.class))).thenReturn(0);
		assertThrows(ResponseStatusException.class, () -> service.saveAccess(null, "U1", "A1",
			"ACTIVE", LocalDate.now(), null, "ADMIN"));
		verifyNoInteractions(licensing);
	}

	@Test void authorizedUserCanListAndDownloadPublishedVersion() {
		var version = new DistributionService.Version("V1", "A1", "App", "1.0", 1, "app.apk",
			"abc", 3, null, null, false, true, LocalDateTime.now());
		var app = new DistributionService.PortalApplication("A1", "APP", "App", version);
		doReturn(List.of(app)).when(licensing).query(contains("user_application_access"),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		doReturn(List.of(version)).when(licensing).query(contains("v.Published=TRUE"),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		doReturn(List.of("APP/1/application.apk")).when(licensing).query(
			startsWith("SELECT Storage_File_Name"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		Path path = Path.of("safe.apk");
		when(storage.resolve("APP/1/application.apk")).thenReturn(path);
		assertEquals(List.of(app), service.portalApps(user));
		assertEquals(path, service.portalDownload(user, "V1"));
	}

	@Test void authorizedAccessIsWrittenOnlyToLicensingDatabase() {
		when(etic.queryForObject(contains("FROM usuarios"), any(Map.class), eq(Integer.class))).thenReturn(1);
		when(licensing.queryForObject(contains("FROM licensed_applications"), any(Map.class), eq(Integer.class))).thenReturn(1);
		var access = new DistributionService.Access("X1", "U1", "A1", "App", "ACTIVE", LocalDate.now(), null, LocalDateTime.now());
		doReturn(List.of(access)).when(licensing).query(contains("WHERE x.Id_Access=:id"),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		assertEquals(access, service.saveAccess(null, "U1", "A1", "ACTIVE", LocalDate.now(), null, "ADMIN"));
		verify(licensing).update(startsWith("INSERT INTO user_application_access"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
		verify(etic, never()).update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
	}

	@Test void uploadVersionWritesMetadataOnlyToLicensingDatabase() {
		var file = new MockMultipartFile("file", "app.apk", "application/octet-stream", "apk".getBytes());
		doReturn(List.of("APP")).when(licensing).query(startsWith("SELECT Code FROM licensed_applications"),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		when(licensing.queryForObject(contains("FROM application_versions"), any(Map.class), eq(Integer.class))).thenReturn(0);
		when(storage.store("APP", 1, file)).thenReturn(new ApkStorage.StoredApk("APP/1/application.apk", "app.apk", "abc", 3));
		var version = new DistributionService.Version("V1", "A1", "App", "1.0", 1, "app.apk",
			"abc", 3, null, null, false, false, LocalDateTime.now());
		doReturn(List.of(version)).when(licensing).query(contains("WHERE v.Id_Version=:id"),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		assertEquals(version, service.upload("A1", "1.0", 1, null, null, false, false, file, "ADMIN"));
		verify(licensing).update(startsWith("INSERT INTO application_versions"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
		verifyNoInteractions(etic);
	}

	@Test void uploadVersionAcceptsMissingVersionCode() {
		var file = new MockMultipartFile("file", "app.apk", "application/octet-stream", "apk".getBytes());
		doReturn(List.of("APP")).when(licensing).query(startsWith("SELECT Code FROM licensed_applications"),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		when(storage.store(eq("APP"), anyString(), eq(file)))
			.thenReturn(new ApkStorage.StoredApk("APP/V1/application.apk", "app.apk", "abc", 3));
		var version = new DistributionService.Version("V1", "A1", "App", "1.0", null, "app.apk",
			"abc", 3, null, null, false, false, LocalDateTime.now());
		doReturn(List.of(version)).when(licensing).query(contains("WHERE v.Id_Version=:id"),
			any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));

		assertEquals(version, service.upload("A1", "1.0", (Long) null,
			null, null, false, false, file, "ADMIN"));
		verify(licensing, never()).queryForObject(contains("FROM application_versions"),
			any(Map.class), eq(Integer.class));
		verify(licensing).update(startsWith("INSERT INTO application_versions"),
			any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
	}
}
