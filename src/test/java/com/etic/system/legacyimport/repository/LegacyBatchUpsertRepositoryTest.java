package com.etic.system.legacyimport.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyBatchUpsertRepositoryTest {
	@Test void classifiesInsertUpdateAndTemporalSkipsInBulk(){
		JdbcTemplate jdbc=mock(JdbcTemplate.class);
		when(jdbc.queryForList("SHOW COLUMNS FROM problemas")).thenReturn(List.of(Map.of("Field","Id_Problema"),Map.of("Field","Component_Comment"),Map.of("Field","Fecha_Mod"),Map.of("Field","Fecha_Creacion")));
		when(jdbc.queryForList(startsWith("SELECT Id_Problema,Fecha_Mod,Fecha_Creacion"),any(Object[].class))).thenReturn(List.of(row("c2","2026-04-01T00:00:00"),row("C3","2026-01-01T00:00:00"),row("C4","2026-01-01T00:00:00"),row("C5","2026-05-01T00:00:00")));
		when(jdbc.queryForList(startsWith("SELECT Id_Problema FROM"),eq(Object.class),any(Object[].class))).thenReturn(List.of("C1","c2","C5"));
		var result=new LegacyBatchUpsertRepository(jdbc).upsert("problemas","Id_Problema",List.of(
			source("C1","2026-01-01T00:00:00",null),source("C2","2026-05-01T00:00:00",null),source("C3","2025-01-01T00:00:00",null),source("C4","2026-01-01T00:00:00",null),source("C5",null,"2026-06-01T00:00:00")));
		assertThat(result.inserted()).isEqualTo(1);assertThat(result.updated()).isEqualTo(2);
		assertThat(result.skippedIds()).extracting(LegacyBatchUpsertRepository.SkippedId::reason).containsExactly(LegacyBatchUpsertRepository.SkipReason.DESTINATION_NEWER,LegacyBatchUpsertRepository.SkipReason.SAME_DATE);
		verify(jdbc).batchUpdate(startsWith("INSERT INTO problemas"),anyList(),eq(1),any(ParameterizedPreparedStatementSetter.class));
		verify(jdbc,times(2)).batchUpdate(startsWith("UPDATE problemas SET"),anyList(),eq(1),any(ParameterizedPreparedStatementSetter.class));
	}
	@Test void normalizesIdentifiersToUppercase(){
		JdbcTemplate jdbc=mock(JdbcTemplate.class);
		when(jdbc.queryForList("SHOW COLUMNS FROM problemas")).thenReturn(List.of(Map.of("Field","Id_Problema"),Map.of("Field","Id_Falla"),Map.of("Field","Component_Comment")));
		when(jdbc.queryForList(startsWith("SELECT Id_Problema FROM"),eq(Object.class),any(Object[].class))).thenReturn(List.of("ABC"));
		new LegacyBatchUpsertRepository(jdbc).upsert("problemas","Id_Problema",List.of(Map.of("Id_Problema","abc","Id_Falla","fault-1","Component_Comment","Conservar Texto")));
		verify(jdbc).batchUpdate(startsWith("INSERT INTO problemas"),argThat((List<Map<String,Object>> rows)->"ABC".equals(rows.get(0).get("Id_Problema"))&&"FAULT-1".equals(rows.get(0).get("Id_Falla"))&&"Conservar Texto".equals(rows.get(0).get("Component_Comment"))),eq(1),any(ParameterizedPreparedStatementSetter.class));
	}
	private Map<String,Object> row(String id,String modified){return Map.of("Id_Problema",id,"Fecha_Mod",Timestamp.valueOf(LocalDateTime.parse(modified)));}
	private Map<String,Object> source(String id,String modified,String created){var row=new java.util.LinkedHashMap<String,Object>();row.put("Id_Problema",id);row.put("Component_Comment","dato");if(modified!=null)row.put("Fecha_Mod",modified);if(created!=null)row.put("Fecha_Creacion",created);return row;}
}
