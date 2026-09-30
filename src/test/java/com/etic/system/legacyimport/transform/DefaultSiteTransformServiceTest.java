package com.etic.system.legacyimport.transform;

import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.report.LegacyEtlReportBuilder;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultSiteTransformServiceTest {
	@Test
	void createsNormalizedContactFromLegacySiteWhenNoneExists() {
		LegacyBatchUpsertRepository repository = mock(LegacyBatchUpsertRepository.class);
		LegacyEtlContext context = context(repository, legacySite());
		when(repository.tableExists("grupos_sitios")).thenReturn(false);
		when(repository.tableExists("sitio_contactos")).thenReturn(true);
		when(repository.query("SELECT Id_Sitio, Id_Sitio_Contacto FROM sitio_contactos ORDER BY Orden, Id_Sitio_Contacto"))
			.thenReturn(List.of());
		when(repository.upsert(anyString(), anyString(), anyList(), anyInt()))
			.thenReturn(LegacyBatchUpsertRepository.BatchResult.empty());

		new DefaultSiteTransformService().transform(context);
		context.flushPending();

		verify(repository).upsert(
			eq("sitio_contactos"),
			eq("Id_Sitio_Contacto"),
			argThat(rows -> rows.size() == 1
				&& "S1".equals(rows.getFirst().get("Id_Sitio"))
				&& "Contacto histórico".equals(rows.getFirst().get("Nombre"))
				&& "Gerencia".equals(rows.getFirst().get("Puesto"))
				&& Integer.valueOf(1).equals(rows.getFirst().get("Orden"))),
			eq(500)
		);
	}

	@Test
	void preservesExistingNormalizedContacts() {
		LegacyBatchUpsertRepository repository = mock(LegacyBatchUpsertRepository.class);
		LegacyEtlContext context = context(repository, legacySite());
		when(repository.tableExists("grupos_sitios")).thenReturn(false);
		when(repository.tableExists("sitio_contactos")).thenReturn(true);
		when(repository.query("SELECT Id_Sitio, Id_Sitio_Contacto FROM sitio_contactos ORDER BY Orden, Id_Sitio_Contacto"))
			.thenReturn(List.of(Map.of("Id_Sitio", "S1", "Id_Sitio_Contacto", "CONTACT-1")));
		when(repository.upsert(anyString(), anyString(), anyList(), anyInt()))
			.thenReturn(LegacyBatchUpsertRepository.BatchResult.empty());

		new DefaultSiteTransformService().transform(context);
		context.flushPending();

		verify(repository, never()).upsert(eq("sitio_contactos"), anyString(), anyList(), anyInt());
	}

	private LegacyEtlContext context(LegacyBatchUpsertRepository repository, Map<String, Object> site) {
		LegacyDatasetStore store = mock(LegacyDatasetStore.class);
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Consumer<Map<String, Object>> consumer = invocation.getArgument(2);
			consumer.accept(site);
			return null;
		}).when(store).forEach(any(Path.class), eq("customerSites"), any());
		return new LegacyEtlContext(
			"JOB",
			Path.of("staged"),
			store,
			repository,
			new LegacyEtlReportBuilder("JOB"),
			500
		);
	}

	private Map<String, Object> legacySite() {
		return Map.of(
			"CustomerSiteID", "S1",
			"CustomerID", "C1",
			"SiteName", "Planta histórica",
			"ContactName", "Contacto histórico",
			"ContactTitle", "Gerencia",
			"DeleteFlag", 0
		);
	}
}
