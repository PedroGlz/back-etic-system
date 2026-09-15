package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import com.etic.system.legacyimport.repository.LegacyImportJobRepository;
import com.etic.system.legacyimport.transform.*;
import com.etic.system.legacyimport.validator.LegacyResultValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.task.TaskExecutor;
import com.etic.system.shared.domain.exception.BusinessValidationException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LegacyEtlExecutionServiceTest {
	@Test
	void executeUsesValidatedStagingWithoutParsingSql(@TempDir Path temporary) throws Exception {
		String id="00000000-0000-0000-0000-000000000001",user="ADMIN";
		Path importsDirectory=temporary.resolve("legacy-imports"),staged=importsDirectory.resolve(id+"-datasets");
		Files.createDirectories(staged);
		StorageProperties storage=new StorageProperties();storage.setPath(temporary.toString());
		LegacyImportJobRepository jobs=mock(LegacyImportJobRepository.class);
		when(jobs.findById(id)).thenReturn(Optional.of(new LegacyImportJob(id,"legacy.sql",LegacyImportStatus.READY,"ANALYSIS",100,LocalDateTime.now(),null,null,user)));
		when(jobs.claimForExecution(id,user)).thenReturn(true);
		LegacyImportService imports=mock(LegacyImportService.class);LegacyImportAnalysis analysis=mock(LegacyImportAnalysis.class);
		when(analysis.alerts()).thenReturn(List.of());when(imports.analysisForExecution(id)).thenReturn(analysis);
		LegacyDatasetStore store=mock(LegacyDatasetStore.class);when(store.isReady(staged)).thenReturn(true);
		TransactionTemplate transactions=mock(TransactionTemplate.class);
		doAnswer(invocation->{@SuppressWarnings("unchecked") Consumer<TransactionStatus> action=invocation.getArgument(0);action.accept(mock(TransactionStatus.class));return null;}).when(transactions).executeWithoutResult(any());
		LegacyEtlExecutionService service=new LegacyEtlExecutionService(jobs,imports,store,mock(LegacyBatchUpsertRepository.class),
			new LegacyImportProperties(),transactions,new ObjectMapper(),mock(CatalogTransformService.class),mock(CustomerTransformService.class),
			mock(SiteTransformService.class),mock(LocationTransformService.class),mock(InspectionTransformService.class),mock(BaselineTransformService.class),
			mock(ProblemTransformService.class),mock(ChronicHistoryTransformService.class),mock(LegacyResultValidationService.class),storage,Runnable::run);

		assertThat(service.execute(id,user).importId()).isEqualTo(id);
		assertThat(Files.readString(importsDirectory.resolve(id+"-report.md"))).contains("# Importación histórica ETIC","Fecha inicio","Fecha fin","Archivo origen: legacy.sql","## IDs omitidos","## Errores");
		verify(store,never()).stage(any(),any());
		assertThat(Files.exists(importsDirectory.resolve(id+".sql"))).isFalse();
	}

	@Test
	void startSchedulesBackendWorkAndRejectsSecondExecution(@TempDir Path temporary) throws Exception {
		String id="00000000-0000-0000-0000-000000000002",user="ADMIN";StorageProperties storage=new StorageProperties();storage.setPath(temporary.toString());
		Path staged=temporary.resolve("legacy-imports").resolve(id+"-datasets");Files.createDirectories(staged);
		LegacyImportJobRepository jobs=mock(LegacyImportJobRepository.class);when(jobs.findById(id)).thenReturn(Optional.of(new LegacyImportJob(id,"legacy.sql",LegacyImportStatus.READY,"ANALYSIS",100,LocalDateTime.now(),null,null,user)));when(jobs.claimForExecution(id,user)).thenReturn(true,false);
		LegacyDatasetStore store=mock(LegacyDatasetStore.class);when(store.isReady(staged)).thenReturn(true);TaskExecutor executor=mock(TaskExecutor.class);
		LegacyEtlExecutionService service=new LegacyEtlExecutionService(jobs,mock(LegacyImportService.class),store,mock(LegacyBatchUpsertRepository.class),new LegacyImportProperties(),mock(TransactionTemplate.class),new ObjectMapper(),mock(CatalogTransformService.class),mock(CustomerTransformService.class),mock(SiteTransformService.class),mock(LocationTransformService.class),mock(InspectionTransformService.class),mock(BaselineTransformService.class),mock(ProblemTransformService.class),mock(ChronicHistoryTransformService.class),mock(LegacyResultValidationService.class),storage,executor);
		service.start(id,user);
		verify(executor).execute(any(Runnable.class));
		assertThatThrownBy(()->service.start(id,user)).isInstanceOf(BusinessValidationException.class).hasMessageContaining("proceso");
		verify(executor,times(1)).execute(any(Runnable.class));
	}
}
