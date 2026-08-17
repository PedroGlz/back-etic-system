package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultChronicHistoryTransformService implements ChronicHistoryTransformService {

	private final ChronicHistoryBuilder builder=new ChronicHistoryBuilder();

	@Override public void transform(LegacyEtlContext c) {
		Map<String,Map<String,Object>> problems=c.index("problems","ProblemID");
		Map<String,String> graph=new java.util.LinkedHashMap<>(); Set<String> relevant=new HashSet<>();
		problems.forEach((id,row)->{String prior=c.text(row,"PriorProblemID");if(id.equalsIgnoreCase(prior))prior=null;graph.put(id,prior);if(prior!=null){relevant.add(id);relevant.add(prior);}if(truth(row.get("IsChronic")))relevant.add(id);});
		Map<String,Map<String,Object>> appearances=c.index("problemInspections","ProblemInspectionID");
		Map<String,Map<String,Object>> inspections=c.index("inspections","InspectionID");
		List<ChronicHistoryBuilder.Appearance> ordered=new ArrayList<>();
		c.forEach("pieProblemInspections",pie->{
			String appearanceId=c.text(pie,"ProblemInspectionID");Map<String,Object> appearance=appearances.get(appearanceId);if(appearance==null)return;
			String problemId=c.text(appearance,"ProblemID");if(!relevant.contains(problemId))return;
			String inspectionId=c.text(appearance,"InspectionID");Map<String,Object> inspection=inspections.get(inspectionId);
			ordered.add(new ChronicHistoryBuilder.Appearance(problemId,appearanceId,c.text(pie,"PIEProblemInspectionID"),inspectionId,
				inspection==null?null:c.text(inspection,"CustomerSiteID"),inspection==null?null:c.date(inspection,"ScheduledStart"),
				inspection==null?null:integer(inspection.get("InspectionNo")),c.date(appearance,"CreateDate")));
		});
		ChronicHistoryBuilder.Result result=builder.build(graph,ordered);
		List<Map<String,Object>> rows=new ArrayList<>();
		for(ChronicHistoryBuilder.HistoryRelation relation:result.relations()){
			c.report().source("historial_problemas");
			rows.add(c.row("Id_Historial_Problema",relation.id(),"Id_Problema",relation.current().pieProblemInspectionId(),
				"Id_Problema_Anterior",relation.previousPieId(),"Id_Problema_Original",relation.originalPieId(),"Estatus","Activo",
				"Id_Inspeccion",relation.current().inspectionId(),"Id_Sitio",relation.current().siteId(),"Fecha_Creacion",relation.current().createdAt()));
			if(rows.size()>=500)c.flush("historial_problemas","Id_Historial_Problema",rows);
		}
		c.flush("historial_problemas","Id_Historial_Problema",rows);
		c.report().chronicFamilies(result.families());c.report().historyRelations(result.relations().size());
	}
	private boolean truth(Object v){return v instanceof Boolean b?b:v!=null&&("1".equals(v.toString())||"true".equalsIgnoreCase(v.toString()));}
	private Integer integer(Object value){return value instanceof Number n?n.intValue():value==null?null:Integer.valueOf(value.toString());}
}
