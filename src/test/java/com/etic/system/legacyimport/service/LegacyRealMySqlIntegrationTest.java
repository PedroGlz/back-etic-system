package com.etic.system.legacyimport.service;

import com.etic.system.legacyimport.report.LegacyEtlReport;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfSystemProperty(named = "legacy.mysql.it", matches = "true")
class LegacyRealMySqlIntegrationTest {
	@Autowired LegacyImportService imports;
	@Autowired LegacyEtlExecutionService execution;
	@Autowired LegacyBatchUpsertRepository repository;

	@Test
	void importsFixtureAndConfirmsRowsInRealMySql() throws Exception {
		String user="LEGACY-INTEGRATION-TEST";
		try(InputStream fixture=getClass().getResourceAsStream("/fixtures/etic-legacy-v1-small.sql")){
			var job=imports.upload(new MockMultipartFile("file","etic-legacy-v1-small.sql","application/sql",fixture),user);
			LegacyEtlReport first=execution.execute(job.id(),user);
			assertExists("clientes","Id_Cliente","C1");assertExists("sitios","Id_Sitio","S1");
			assertExists("equipos","Id_Equipo","E1");assertExists("categorias_equipos","id_categoria_equipo","EG1");
			assertExists("fabricantes","Id_Fabricante","M1");assertExists("fallas","Id_Falla","F1");assertExists("tipo_fallas","Id_Tipo_Falla","FT1");
			assertExists("fases","Id_Fase","PH1");assertExists("tipo_ambientes","Id_Tipo_Ambiente","ENV1");assertExists("severidades","Id_Severidad","SV1");
			assertExists("tipo_prioridades","Id_Tipo_Prioridad","PS1");assertExists("tipo_inspecciones","Id_Tipo_Inspeccion","IT1");assertExists("causa_principal","Id_Causa_Raiz","RC1");
			assertExists("ubicaciones","Id_Ubicacion","L1","L2","L3");assertExists("inspecciones","Id_Inspeccion","I1","I2");
			assertExists("inspecciones_det","Id_Inspeccion_Det","ID1","ID2");assertExists("linea_base","Id_Linea_Base","B1");
			assertExists("problemas","Id_Problema","PIE0","PIE1","PIE2");
			Map<String,Object> baseline=repository.query("SELECT MTA,Temp_max,Temp_amb FROM linea_base WHERE Id_Linea_Base=?","B1").getFirst();
			assertThat(((Number)baseline.get("MTA")).doubleValue()).isEqualTo(104D);
			assertThat(((Number)baseline.get("Temp_max")).doubleValue()).isEqualTo(86D);
			assertThat(((Number)baseline.get("Temp_amb")).doubleValue()).isEqualTo(77D);
			assertThat(first.historyRelations()).isPositive();
			assertThat(first.tables()).allSatisfy(table->assertThat(table.source()).isEqualTo(table.inserted()+table.updated()+table.skipped()+table.errors()));
			LegacyEtlReport second=execution.execute(job.id(),user);
			assertThat(second.tables()).allSatisfy(table->assertThat(table.source()).isEqualTo(table.inserted()+table.updated()+table.skipped()+table.errors()));
		}
	}

	private void assertExists(String table,String key,String... ids){String placeholders=String.join(",",java.util.Arrays.stream(ids).map(ignored->"?").toList());List<Map<String,Object>> rows=repository.query("SELECT "+key+" FROM "+table+" WHERE "+key+" IN ("+placeholders+")",(Object[])ids);assertThat(rows).hasSize(ids.length);}
}
