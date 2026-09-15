package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.parser.LegacySqlScriptParser;
import com.etic.system.legacyimport.parser.LegacyDatasetStore;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class LegacyImportServiceTest {
	@Test
	void uploadsWithInternalNameAndReturnsRealAnalysis(@TempDir Path temporary) throws Exception {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyImportCleanupService cleanup = mock(LegacyImportCleanupService.class);
		StorageProperties storage = new StorageProperties(); storage.setPath(temporary.toString());
		ObjectMapper mapper = new ObjectMapper();
		LegacySqlScriptParser parser = spy(new LegacySqlScriptParser(mapper));
		LegacyImportService service = new LegacyImportService(jobs, new LegacyDatasetStore(mapper, parser),
			new LegacyImportProperties(), storage, cleanup);
		try (InputStream fixture = getClass().getResourceAsStream("/fixtures/etic-legacy-v1-small.sql")) {
			MockMultipartFile upload = new MockMultipartFile("file", "respaldo.sql", "application/sql", fixture);
			when(jobs.findById(any())).thenAnswer(invocation -> java.util.Optional.of(new LegacyImportJob(
				invocation.getArgument(0), "respaldo.sql", LegacyImportStatus.READY, "ANALYSIS", 100,
				java.time.LocalDateTime.now(), null, null, "ADMIN")));

			LegacyImportJob job = service.upload(upload, "ADMIN");

			assertThat(job.status()).isEqualTo(LegacyImportStatus.READY);
			assertThat(Files.isRegularFile(temporary.resolve("legacy-imports").resolve(job.id() + ".sql"))).isTrue();
			Path staged=temporary.resolve("legacy-imports").resolve(job.id()+"-datasets");
			assertThat(Files.isRegularFile(staged.resolve("analysis.json"))).isTrue();
			assertThat(Files.readAllLines(staged.resolve("problems.ndjson"))).hasSize(3);
			assertThat(service.analysis(job.id(), "ADMIN").problemas()).isEqualTo(3);
			verify(parser,times(1)).analyzeAndStage(any(),any(),any(Long.class));
			verify(parser,never()).stage(any(),any());
		}
	}

	@Test
	void recoversAnalysisFromStagingWithoutSqlAfterRestart(@TempDir Path temporary) throws Exception {
		LegacyImportJobRepository jobs=mock(LegacyImportJobRepository.class);StorageProperties storage=new StorageProperties();storage.setPath(temporary.toString());
		ObjectMapper mapper=new ObjectMapper();LegacySqlScriptParser parser=spy(new LegacySqlScriptParser(mapper));
		LegacyImportService first=new LegacyImportService(jobs,new LegacyDatasetStore(mapper,parser),new LegacyImportProperties(),storage,mock(LegacyImportCleanupService.class));
		when(jobs.findById(any())).thenAnswer(invocation->java.util.Optional.of(new LegacyImportJob(invocation.getArgument(0),"respaldo.sql",LegacyImportStatus.READY,"ANALYSIS",100,java.time.LocalDateTime.now(),null,null,"ADMIN")));
		try(InputStream fixture=getClass().getResourceAsStream("/fixtures/etic-legacy-v1-small.sql")){
			LegacyImportJob job=first.upload(new MockMultipartFile("file","respaldo.sql","application/sql",fixture),"ADMIN");
			Files.delete(temporary.resolve("legacy-imports").resolve(job.id()+".sql"));
			LegacyImportService restarted=new LegacyImportService(jobs,new LegacyDatasetStore(mapper,parser),new LegacyImportProperties(),storage,mock(LegacyImportCleanupService.class));
			assertThat(restarted.analysis(job.id(),"ADMIN").problemas()).isEqualTo(3);
			verify(parser,times(1)).analyzeAndStage(any(),any(),any(Long.class));
			verify(parser,never()).analyze(any(),any(Long.class));
		}
	}

	@Test
	void rejectsJsonFiles(@TempDir Path temporary) {
		StorageProperties storage = new StorageProperties(); storage.setPath(temporary.toString());
		ObjectMapper mapper=new ObjectMapper();
		LegacyImportService service = new LegacyImportService(mock(LegacyImportJobRepository.class),
			new LegacyDatasetStore(mapper,new LegacySqlScriptParser(mapper)), new LegacyImportProperties(), storage, mock(LegacyImportCleanupService.class));
		MockMultipartFile upload = new MockMultipartFile("file", "legacy.json", "application/json", "{}".getBytes());
		assertThatThrownBy(() -> service.upload(upload, "ADMIN")).isInstanceOf(BusinessValidationException.class)
			.hasMessageContaining(".sql");
	}

	@Test
	void activeIsEmptyWhenThereIsNoActiveJob(@TempDir Path temporary) {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyImportService service = activeService(jobs, mock(LegacyDatasetStore.class), temporary);
		when(jobs.findActiveByUser("ADMIN")).thenReturn(java.util.Optional.empty());
		assertThat(service.active("ADMIN")).isEmpty();
	}

	@Test
	void activeReturnsReadyJobWithCompleteStaging(@TempDir Path temporary) {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyDatasetStore store = mock(LegacyDatasetStore.class);
		LegacyImportService service = activeService(jobs, store, temporary);
		LegacyImportJob job = activeJob(LegacyImportStatus.READY);
		when(jobs.findActiveByUser("ADMIN")).thenReturn(java.util.Optional.of(job));
		when(store.isReady(stagingPath(temporary))).thenReturn(true);
		assertThat(service.active("ADMIN")).contains(job);
	}

	@Test
	void activeReturnsProcessingJobWithCompleteStaging(@TempDir Path temporary) {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyDatasetStore store = mock(LegacyDatasetStore.class);
		LegacyImportService service = activeService(jobs, store, temporary);
		LegacyImportJob job = activeJob(LegacyImportStatus.PROCESSING);
		when(jobs.findActiveByUser("ADMIN")).thenReturn(java.util.Optional.of(job));
		when(store.isReady(stagingPath(temporary))).thenReturn(true);
		assertThat(service.active("ADMIN")).contains(job);
	}

	@Test
	void activeRejectsReadyJobWithoutStaging(@TempDir Path temporary) {
		assertOrphanIsFailed(LegacyImportStatus.READY, temporary);
	}

	@Test
	void activeRejectsProcessingJobWithoutStaging(@TempDir Path temporary) {
		assertOrphanIsFailed(LegacyImportStatus.PROCESSING, temporary);
	}

	@Test
	void activeDoesNotReturnCompletedJob(@TempDir Path temporary) {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyImportService service = activeService(jobs, mock(LegacyDatasetStore.class), temporary);
		when(jobs.findActiveByUser("ADMIN")).thenReturn(java.util.Optional.of(activeJob(LegacyImportStatus.COMPLETED)));
		assertThat(service.active("ADMIN")).isEmpty();
	}

	@Test
	void activeDoesNotReturnFailedJob(@TempDir Path temporary) {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyImportService service = activeService(jobs, mock(LegacyDatasetStore.class), temporary);
		when(jobs.findActiveByUser("ADMIN")).thenReturn(java.util.Optional.of(activeJob(LegacyImportStatus.FAILED)));
		assertThat(service.active("ADMIN")).isEmpty();
	}

	private void assertOrphanIsFailed(LegacyImportStatus status, Path temporary) {
		LegacyImportJobRepository jobs = mock(LegacyImportJobRepository.class);
		LegacyDatasetStore store = mock(LegacyDatasetStore.class);
		LegacyImportService service = activeService(jobs, store, temporary);
		when(jobs.findActiveByUser("ADMIN")).thenReturn(java.util.Optional.of(activeJob(status)));
		when(store.isReady(stagingPath(temporary))).thenReturn(false);

		assertThat(service.active("ADMIN")).isEmpty();
		verify(jobs).updateState(eq("ID"), eq(LegacyImportStatus.FAILED), eq("VALIDATION"), eq(100),
			eq("Staging temporal perdido o incompleto"), any(java.time.LocalDateTime.class));
	}

	private LegacyImportService activeService(LegacyImportJobRepository jobs, LegacyDatasetStore store, Path temporary) {
		StorageProperties storage = new StorageProperties();
		storage.setPath(temporary.toString());
		return new LegacyImportService(jobs, store, new LegacyImportProperties(), storage,
			mock(LegacyImportCleanupService.class));
	}

	private LegacyImportJob activeJob(LegacyImportStatus status) {
		return new LegacyImportJob("ID", "legacy.sql", status, "VALIDATION", 100,
			java.time.LocalDateTime.now(), null, null, "ADMIN");
	}

	private Path stagingPath(Path temporary) {
		return temporary.resolve("legacy-imports").resolve("ID-datasets");
	}
}
