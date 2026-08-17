package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.parser.LegacySqlScriptParser;
import com.etic.system.legacyimport.repository.LegacyImportJobRepository;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyImportServiceTest {
	@Test
	void uploadsWithInternalNameAndReturnsRealAnalysis(@TempDir Path temporary) throws Exception {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyImportCleanupService cleanup = mock(LegacyImportCleanupService.class);
		StorageProperties storage = new StorageProperties(); storage.setPath(temporary.toString());
		LegacyImportService service = new LegacyImportService(jobs, new LegacySqlScriptParser(new ObjectMapper()),
			new LegacyImportProperties(), storage, cleanup);
		try (InputStream fixture = getClass().getResourceAsStream("/fixtures/etic-legacy-v1-small.sql")) {
			MockMultipartFile upload = new MockMultipartFile("file", "respaldo.sql", "application/sql", fixture);
			when(jobs.findById(any())).thenAnswer(invocation -> java.util.Optional.of(new LegacyImportJob(
				invocation.getArgument(0), "respaldo.sql", LegacyImportStatus.READY, "ANALYSIS", 100,
				java.time.LocalDateTime.now(), null, null, "ADMIN")));

			LegacyImportJob job = service.upload(upload, "ADMIN");

			assertThat(job.status()).isEqualTo(LegacyImportStatus.READY);
			assertThat(Files.isRegularFile(temporary.resolve("legacy-imports").resolve(job.id() + ".sql"))).isTrue();
			assertThat(service.analysis(job.id(), "ADMIN").problemas()).isEqualTo(3);
		}
	}

	@Test
	void rejectsJsonFiles(@TempDir Path temporary) {
		StorageProperties storage = new StorageProperties(); storage.setPath(temporary.toString());
		LegacyImportService service = new LegacyImportService(mock(LegacyImportJobRepository.class),
			new LegacySqlScriptParser(new ObjectMapper()), new LegacyImportProperties(), storage, mock(LegacyImportCleanupService.class));
		MockMultipartFile upload = new MockMultipartFile("file", "legacy.json", "application/json", "{}".getBytes());
		assertThatThrownBy(() -> service.upload(upload, "ADMIN")).isInstanceOf(BusinessValidationException.class)
			.hasMessageContaining(".sql");
	}
}
