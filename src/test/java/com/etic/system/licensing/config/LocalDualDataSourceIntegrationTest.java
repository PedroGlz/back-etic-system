package com.etic.system.licensing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "ETIC_LOCAL_DB_INTEGRATION", matches = "true")
class LocalDualDataSourceIntegrationTest {
	@Autowired @Qualifier("dataSource") DataSource etic;
	@Autowired @Qualifier("licensingDataSource") DataSource licensing;

	@Test void bothDataSourcesConnectToTheirOwnSchemas() throws Exception {
		try (var connection = etic.getConnection();
			var statement = connection.createStatement();
			var result = statement.executeQuery("SELECT DATABASE()")) {
			assertTrue(result.next());
			assertEquals("etic_system", result.getString(1));
		}
		try (var connection = licensing.getConnection();
			var statement = connection.createStatement();
			var database = statement.executeQuery("SELECT DATABASE()")) {
			assertTrue(database.next());
			assertEquals("license_system", database.getString(1));
			try (var tables = statement.executeQuery(
				"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('licensed_applications','licensed_devices','licenses')")) {
				assertTrue(tables.next());
				assertEquals(3, tables.getInt(1));
			}
		}
	}
}
