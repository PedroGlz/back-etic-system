package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultInspectionTransformService implements InspectionTransformService {

	@Override public void transform(LegacyEtlContext c) {
		@SuppressWarnings("unchecked") Map<String,String> groups=(Map<String,String>)c.shared().getOrDefault("groupByCustomer",Map.of());
		Map<String,Map<String,Object>> inspections=c.index("inspections","InspectionID");
		Map<String,String> units=new HashMap<>();
		inspections.forEach((id,row)->units.put(id,c.text(row,"TemperatureUnit")));
		c.transform("inspections","inspecciones","Id_Inspeccion",r->c.row(
			"Id_Inspeccion",c.text(r,"InspectionID"),"Id_Sitio",c.text(r,"CustomerSiteID"),"Id_Cliente",c.text(r,"CustomerID"),
			"Id_Grupo_Sitios",groups.get(c.text(r,"CustomerID")),"Id_Status_Inspeccion",c.text(r,"InspectionStatusID"),
			"Fecha_Inicio",c.value(r,"ScheduledStart"),"Fecha_Fin",c.value(r,"ScheduledEnd"),"Fotos_Ruta",c.text(r,"PhotoPath"),
			"No_Dias",c.value(r,"NoOfDays"),"Unidad_Temp","C","No_Inspeccion",c.value(r,"InspectionNo"),
			"No_Inspeccion_Ant",c.value(r,"LastInspectionNo"),"Estatus",inactive(r)?"Inactivo":"Activo",
			"Creado_Por",c.text(r,"CreateUserID"),"Fecha_Creacion",c.value(r,"CreateDate"),"Modificado_Por",c.text(r,"LastUserID"),"Fecha_Mod",c.value(r,"LastModified")));
		Set<String> unique=new HashSet<>();
		c.transform("inspectionDetails","inspecciones_det","Id_Inspeccion_Det",r->{
			String inspectionId=c.text(r,"InspectionID"), locationId=c.text(r,"LocationID"), logical=inspectionId+"\u0000"+locationId;
			if(!unique.add(logical)) throw new IllegalArgumentException("InspectionID + LocationID duplicado: "+inspectionId+" / "+locationId);
			Map<String,Object> inspection=inspections.get(inspectionId);
			if(inspection==null) throw new IllegalArgumentException("InspectionID inexistente en detalle: "+inspectionId);
			return c.row("Id_Inspeccion_Det",c.text(r,"InspectionDetailID"),"Id_Inspeccion",inspectionId,"Id_Ubicacion",locationId,
				"Id_Sitio",c.text(inspection,"CustomerSiteID"),"Id_Status_Inspeccion_Det",c.text(r,"InspectionDetailStatusID"),
				"Notas_Inspeccion",c.text(r,"TestStatusNote"),"Estatus","Activo","Creado_Por",c.text(r,"CreateUserID"),
				"Fecha_Creacion",c.value(r,"CreateDate"),"Modificado_Por",c.text(r,"LastUserID"),"Fecha_Mod",c.value(r,"LastModified"));
		});
		c.shared().put("inspections",inspections); c.shared().put("temperatureUnits",units);
	}
	private boolean inactive(Map<String,Object> row){Object value=row.get("DeleteFlag");return value!=null&&!"0".equals(value.toString());}
}
