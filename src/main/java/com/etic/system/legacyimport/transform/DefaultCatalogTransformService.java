package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultCatalogTransformService implements CatalogTransformService {

	@Override
	public void transform(LegacyEtlContext c) {
		c.transform("inspectionStatuses", "estatus_inspeccion", "Id_Status_Inspeccion", r -> common(c, r,
			c.row("Id_Status_Inspeccion", c.text(r,"InspectionStatusID"), "Status_Inspeccion", c.text(r,"Name"), "Desc_Status", c.text(r,"Description"))));
		c.transform("inspectionDetailStatuses", "estatus_inspeccion_det", "Id_Status_Inspeccion_Det", r -> common(c, r,
			c.row("Id_Status_Inspeccion_Det", c.text(r,"InspectionDetailStatusID"), "Estatus_Inspeccion_Det", detailStatusCode(c,r), "Desc_Estatus_Det", detailStatusDescription(c,r))));
		c.transform("inspectionTypes", "tipo_inspecciones", "Id_Tipo_Inspeccion", r -> common(c, r,
			c.row("Id_Tipo_Inspeccion", c.text(r,"InspectionTypeID"), "Tipo_Inspeccion", c.text(r,"Name"), "Desc_Inspeccion", c.text(r,"Description"))));
		c.transform("priorityStatus", "tipo_prioridades", "Id_Tipo_Prioridad", r -> common(c, r,
			c.row("Id_Tipo_Prioridad", c.text(r,"PriorityStatusID"), "Tipo_Prioridad", c.text(r,"Name"), "Desc_Prioridad", c.text(r,"Description"))));
		c.transform("faultTypes", "tipo_fallas", "Id_Tipo_Falla", r -> common(c, r,
			c.row("Id_Tipo_Falla", c.text(r,"FaultTypeID"), "Id_Tipo_Inspeccion", c.text(r,"InspectionTypeID"), "Tipo_Falla", c.text(r,"Name"), "Desc_Tipo_Falla", c.text(r,"Description"))));
		Map<String,Map<String,Object>> faultTypes = c.index("faultTypes", "FaultTypeID");
		c.transform("faults", "fallas", "Id_Falla", r -> {
			Map<String,Object> type = faultTypes.get(c.text(r,"FaultTypeID"));
			return common(c, r, c.row("Id_Falla", c.text(r,"FaultID"), "Id_Tipo_Falla", c.text(r,"FaultTypeID"),
				"Id_Tipo_Inspeccion", type == null ? null : c.text(type,"InspectionTypeID"), "Falla", c.text(r,"Fault")));
		});
		c.transform("piePhases", "fases", "Id_Fase", r -> common(c, r,
			c.row("Id_Fase", c.text(r,"PIEPhaseID"), "Nombre_Fase", c.text(r,"Name"), "Descripcion", c.text(r,"Description"))));
		c.transform("pieEnvironments", "tipo_ambientes", "Id_Tipo_Ambiente", r -> c.row(
			"Id_Tipo_Ambiente", c.text(r,"PIEEnvironmentID"), "Nombre", c.text(r,"Name"), "Descripcion", c.text(r,"Description"), "Adjust", c.value(r,"Adjust"), "Estatus", "Activo"));
		c.transform("problemSeverity", "severidades", "Id_Severidad", r -> c.row(
			"Id_Severidad", c.text(r,"ProblemSeverityID"), "Severidad", c.text(r,"Name"), "Descripcion", severityDescription(c,r)));
		c.transform("manufacturers", "fabricantes", "Id_Fabricante", r -> common(c, r, c.row(
			"Id_Fabricante", c.text(r,"ManufacturerID"), "Id_Tipo_Inspeccion", c.text(r,"InspectionTypeID"), "Fabricante", c.text(r,"Name"), "Desc_Fabricante", c.text(r,"Description"))));
		c.transform("equipment", "equipos", "Id_Equipo", r -> common(c, r, c.row(
			"Id_Equipo", c.text(r,"EquipmentID"), "Equipo", c.text(r,"Name"), "Descr_equipo", c.text(r,"Description"))));
		c.forEach("equipmentGroups",r->{c.report().source("categorias_equipos");c.report().skipped("categorias_equipos",c.text(r,"EquipmentGroupID"),"PROTECTED_CURRENT_CATALOG",null,null);});
		c.transform("rootCauses", "causa_principal", "Id_Causa_Raiz", r -> common(c, r, c.row(
			"Id_Causa_Raiz", c.text(r,"RootCauseID"), "Id_Tipo_Inspeccion", c.text(r,"InspectionTypeID"), "Id_Falla", c.text(r,"FaultID"), "Causa_Raiz", c.text(r,"RootCause"))));
	}

	private Map<String,Object> common(LegacyEtlContext c, Map<String,Object> source, Map<String,Object> target) {
		target.put("Estatus", active(source));
		copy(c, source, target, "CreateUserID", "Creado_Por"); copy(c, source, target, "CreateDate", "Fecha_Creacion");
		copy(c, source, target, "LastUserID", "Modificado_Por"); copy(c, source, target, "LastModified", "Fecha_Mod");
		return target;
	}
	private void copy(LegacyEtlContext c, Map<String,Object> source, Map<String,Object> target, String from, String to) { if (source.get(from) != null) target.put(to, source.get(from)); }
	private String active(Map<String,Object> row) { Object flag=row.get("DeleteFlag"); return flag != null && !"0".equals(flag.toString()) ? "Inactivo" : "Activo"; }
	private String severityDescription(LegacyEtlContext c, Map<String,Object> row) {
		String cRange=c.text(row,"BaseCRange"), fRange=c.text(row,"BaseFRange"), assessment=c.text(row,"AssessmentName");
		return cRange != null ? cRange : fRange != null ? fRange : assessment;
	}
	private String detailStatusCode(LegacyEtlContext c,Map<String,Object> row){for(String field:java.util.List.of("Code","StatusCode","Abbreviation","ShortName")){String value=c.text(row,field);if(value!=null)return value.length()<=10?value:value.substring(0,10);}String name=c.text(row,"Name");if(name==null)return null;if(name.length()<=10)return name;c.report().warning("estatus_inspeccion_det","Nombre de estatus reducido al límite MySQL (10): "+c.text(row,"InspectionDetailStatusID"));return name.substring(0,10);}
	private String detailStatusDescription(LegacyEtlContext c,Map<String,Object> row){String description=c.text(row,"Description");return description!=null?description:c.text(row,"Name");}
}
