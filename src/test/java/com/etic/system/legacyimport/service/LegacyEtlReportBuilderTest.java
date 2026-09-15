package com.etic.system.legacyimport.service;

import com.etic.system.legacyimport.report.LegacyEtlReport;
import com.etic.system.legacyimport.report.LegacyEtlReportBuilder;
import com.etic.system.legacyimport.report.LegacyRecordOutcome;
import com.etic.system.legacyimport.report.TableReconciliation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyEtlReportBuilderTest {
	@Test
	void rollbackTruncatesDetailsAndRestoresReconciliation() {
		LegacyEtlReportBuilder report=new LegacyEtlReportBuilder("JOB");
		report.source("clientes");report.inserted("clientes",1);report.persisted("clientes","C1","INSERTED");
		LegacyEtlReportBuilder.Checkpoint checkpoint=report.checkpoint();
		report.source("sitios");report.updated("sitios",1);report.persisted("sitios","S1","UPDATED");report.warning("sitios","temporal");
		report.restore(checkpoint);
		LegacyEtlReport result=report.build();
		assertThat(result.tables()).extracting(TableReconciliation::table).containsExactly("clientes");
		assertThat(result.outcomes()).extracting(LegacyRecordOutcome::id).containsExactly("C1");
		assertThat(result.warnings()).isEmpty();
	}
}
