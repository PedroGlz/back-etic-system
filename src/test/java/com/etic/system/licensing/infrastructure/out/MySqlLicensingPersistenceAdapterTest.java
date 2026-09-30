package com.etic.system.licensing.infrastructure.out;

import com.etic.system.licensing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MySqlLicensingPersistenceAdapterTest {
	private NamedParameterJdbcTemplate licensing;
	private NamedParameterJdbcTemplate etic;
	private MySqlLicensingPersistenceAdapter adapter;

	@BeforeEach void setUp() {
		licensing = mock(NamedParameterJdbcTemplate.class);
		etic = mock(NamedParameterJdbcTemplate.class);
		adapter = new MySqlLicensingPersistenceAdapter(licensing, etic);
	}

	@Test void licensingReadsUseOnlyLicensingJdbc() {
		adapter.findApplications();
		adapter.findDevices();
		adapter.findLicenses();
		verify(licensing).query(startsWith("SELECT * FROM licensed_applications"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		verify(licensing).query(startsWith("SELECT d.*"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		verify(licensing).query(startsWith("SELECT l.*"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		verifyNoInteractions(etic);
	}

	@Test void userValidationUsesOnlyEticJdbc() {
		when(etic.queryForObject(contains("FROM usuarios"), any(Map.class), eq(Integer.class))).thenReturn(1);
		assertTrue(adapter.activeUserExists("U1"));
		verifyNoInteractions(licensing);
	}

	@Test void applicationUpdateDoesNotWriteCode() {
		LicensedApplication app = new LicensedApplication("A1", "STABLE_CODE", "Nuevo nombre", "pkg", LicensingMode.DEVICE_ONLY, "ACTIVE", LocalDateTime.now(), LocalDateTime.now());
		when(licensing.query(contains("FROM licensed_applications WHERE"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(List.of(app));

		assertEquals("STABLE_CODE", adapter.updateApplication("A1", "Nuevo nombre", "pkg", LicensingMode.DEVICE_ONLY, "ACTIVE", "ADMIN").code());
		verify(licensing).update(argThat(sql -> sql.startsWith("UPDATE licensed_applications SET Name=") && !sql.contains("Code=")), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
	}

	@Test void applicationDeviceAndLicenseWritesUseOnlyLicensingJdbc() {
		LicensedApplication app = new LicensedApplication("A1", "APP", "App", "pkg", LicensingMode.DEVICE_ONLY, "ACTIVE", LocalDateTime.now(), null);
		LicensedDevice device = new LicensedDevice("D1", "UUID", "Tablet", null, null, null, "ACTIVE", "AUTO", null, null, null, "FINGERPRINT", "EC", "UNKNOWN", false, false, null, LocalDateTime.now(), LocalDateTime.now(), null, null, null, null, null);
		License license = new License("L1", "A1", "APP", "App", LicensingMode.DEVICE_ONLY, "D1", "UUID", "Tablet", null, null, LocalDate.now(), LocalDate.now().plusDays(30), LicenseStatus.ACTIVE, "ACTIVE", "READY", LocalDateTime.now(), null);
		doReturn(List.of(app)).when(licensing).query(contains("FROM licensed_applications WHERE"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		doReturn(List.of(device)).when(licensing).query(contains("FROM licensed_devices d WHERE"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));
		doReturn(List.of(), List.of(license)).when(licensing).query(startsWith("SELECT l.*"), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class));

		assertEquals(app, adapter.createApplication("A1", "APP", "App", "pkg", LicensingMode.DEVICE_ONLY, "ACTIVE", "ADMIN"));
		assertEquals(device, adapter.updateDevice("D1", "Tablet", null, null, null, null, "ADMIN"));
		assertEquals(license, adapter.saveLicense("L1", "A1", "D1", null, license.validFrom(), license.validUntil(), LicenseStatus.ACTIVE, "ADMIN"));

		verify(licensing).update(startsWith("INSERT INTO licensed_applications"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
		verify(licensing).update(startsWith("UPDATE licensed_devices"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
		verify(licensing).update(startsWith("INSERT INTO licenses"), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
		verifyNoInteractions(etic);
	}
}
