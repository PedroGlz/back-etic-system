package com.etic.system.legacyimport.transform;

import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.report.LegacyEtlReportBuilder;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyEtlContextTest {
	@Test
	void combinesFlushesIntoOneUpsertPerTableAndPhase(){
		LegacyBatchUpsertRepository repository=mock(LegacyBatchUpsertRepository.class);
		when(repository.upsert(eq("clientes"),eq("Id_Cliente"),anyList(),eq(500))).thenReturn(LegacyBatchUpsertRepository.BatchResult.empty());
		LegacyEtlContext context=new LegacyEtlContext("JOB",Path.of("staged"),mock(LegacyDatasetStore.class),repository,new LegacyEtlReportBuilder("JOB"),500);
		List<Map<String,Object>> first=new ArrayList<>(List.of(row("C1"))),second=new ArrayList<>(List.of(row("C2")));
		context.flush("clientes","Id_Cliente",first);context.flush("clientes","Id_Cliente",second);
		verify(repository,never()).upsert(anyString(),anyString(),anyList(),anyInt());
		context.flushPending();
		verify(repository,times(1)).upsert(eq("clientes"),eq("Id_Cliente"),argThat(rows->rows.size()==2),eq(500));
	}
	private Map<String,Object> row(String id){Map<String,Object> row=new LinkedHashMap<>();row.put("Id_Cliente",id);return row;}
}
