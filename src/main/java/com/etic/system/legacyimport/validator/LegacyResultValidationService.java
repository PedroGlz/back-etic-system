package com.etic.system.legacyimport.validator;

import com.etic.system.legacyimport.report.LegacyEtlReportBuilder;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LegacyResultValidationService {
	private final LegacyBatchUpsertRepository repository;
	public LegacyResultValidationService(LegacyBatchUpsertRepository repository){this.repository=repository;}
	public void validate(LegacyEtlReportBuilder report){
		List<Check> checks=List.of(
			new Check("sitios","SELECT COUNT(*) total FROM sitios s LEFT JOIN clientes c ON c.Id_Cliente=s.Id_Cliente WHERE c.Id_Cliente IS NULL"),
			new Check("ubicaciones","SELECT COUNT(*) total FROM ubicaciones u LEFT JOIN sitios s ON s.Id_Sitio=u.Id_Sitio WHERE s.Id_Sitio IS NULL"),
			new Check("inspecciones_det","SELECT COUNT(*) total FROM inspecciones_det d LEFT JOIN inspecciones i ON i.Id_Inspeccion=d.Id_Inspeccion LEFT JOIN ubicaciones u ON u.Id_Ubicacion=d.Id_Ubicacion WHERE i.Id_Inspeccion IS NULL OR u.Id_Ubicacion IS NULL"),
			new Check("linea_base","SELECT COUNT(*) total FROM linea_base b LEFT JOIN inspecciones i ON i.Id_Inspeccion=b.Id_Inspeccion LEFT JOIN ubicaciones u ON u.Id_Ubicacion=b.Id_Ubicacion WHERE i.Id_Inspeccion IS NULL OR u.Id_Ubicacion IS NULL"),
			new Check("problemas","SELECT COUNT(*) total FROM problemas p LEFT JOIN inspecciones i ON i.Id_Inspeccion=p.Id_Inspeccion LEFT JOIN ubicaciones u ON u.Id_Ubicacion=p.Id_Ubicacion WHERE i.Id_Inspeccion IS NULL OR u.Id_Ubicacion IS NULL"));
		for(Check check:checks){if(!repository.tableExists(check.table))continue;long count=((Number)repository.query(check.sql).getFirst().get("total")).longValue();report.orphans(check.table,count);if(count>0)report.warning(check.table,"Validación final: "+count+" relaciones huérfanas en "+check.table);}
	}
	private record Check(String table,String sql){}
}
