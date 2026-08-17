package com.etic.system.legacyimport.model;

import java.util.List;

public record LegacyImportAnalysis(
	long clientes,
	long sitios,
	long equipos,
	long categoriasEquipos,
	long fabricantes,
	long tiposInspeccion,
	long estatusInspeccion,
	long estatusDetalle,
	long inspecciones,
	long detalles,
	long ubicaciones,
	long lineasBase,
	long fotosLineaBase,
	long problemas,
	long problemasCronicos,
	long aparicionesProblemas,
	long aparicionesPie,
	long fotosProblemas,
	long severidades,
	long prioridades,
	long fallas,
	long tiposFalla,
	long causas,
	long fases,
	long ambientes,
	long relacionesEquipoFalla,
	List<LegacyValidationIssue> alerts
) {
	public boolean hasErrors() {
		return alerts.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
	}
}
