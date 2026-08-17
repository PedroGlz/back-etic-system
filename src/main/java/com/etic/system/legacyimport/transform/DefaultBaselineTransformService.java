package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultBaselineTransformService implements BaselineTransformService {

	@Override public void transform(LegacyEtlContext c) {
		Map<String,Map<String,Object>> inspections=c.index("inspections","InspectionID");
		Map<String,Map<String,Object>> locations=c.index("locations","LocationID");
		Map<String,String> details=new HashMap<>();
		c.forEach("inspectionDetails",r->details.put(key(c.text(r,"InspectionID"),c.text(r,"LocationID")),c.text(r,"InspectionDetailID")));
		Map<String,List<Map<String,Object>>> photos=new HashMap<>();
		c.forEach("locationBaselinePhotos",r->photos.computeIfAbsent(c.text(r,"BaselineID"),ignored->new ArrayList<>()).add(r));
		photos.values().forEach(list->list.sort(Comparator.comparing(r->safe(c.text(r,"CreateDate"))+safe(c.text(r,"BaselinePhotoID")))));
		c.transform("locationBaselines","linea_base","Id_Linea_Base",r->{
			String inspectionId=c.text(r,"InspectionID"), locationId=c.text(r,"LocationID");
			Map<String,Object> inspection=inspections.get(inspectionId);
			if(inspection==null) throw new IllegalArgumentException("InspectionID inexistente en línea base: "+inspectionId);
			String unit=c.text(inspection,"TemperatureUnit");
			Double measured=temperature(c,r,"MeasuredBaseline",unit,inspectionId);
			Double threshold=temperature(c,r,"CurrentThreshold",unit,inspectionId);
			Double ambient=temperature(c,r,"AmbientBaseline",unit,inspectionId);
			List<Map<String,Object>> baselinePhotos=photos.getOrDefault(c.text(r,"BaselineID"),List.of());
			if(baselinePhotos.size()>1){c.report().additionalPhotos(baselinePhotos.size()-1);c.report().warning("linea_base","Fotos adicionales de línea base: "+c.text(r,"BaselineID"));}
			Map<String,Object> mainPhoto=baselinePhotos.isEmpty()?null:baselinePhotos.getFirst();
			Map<String,Object> location=locations.get(locationId);
			String detailId=details.get(key(inspectionId,locationId));
			return c.row("Id_Linea_Base",c.text(r,"BaselineID"),"Id_Ubicacion",locationId,"Id_Inspeccion",inspectionId,
				"Id_Inspeccion_Det",detailId,"Id_Sitio",c.text(inspection,"CustomerSiteID"),"MTA",measured,"Temp_max",threshold,"Temp_amb",ambient,
				"Notas",c.text(r,"CustomerNotes"),"Archivo_IR",mainPhoto==null?null:c.text(mainPhoto,"FileName"),
				"Archivo_ID",location==null?null:c.text(location,"PhotoFilename"),"Ruta",locationPath(c,locationId),
				"Estatus",inactive(r)?"Inactivo":"Activo","Creado_Por",c.text(r,"CreateUserID"),"Fecha_Creacion",c.value(r,"CreateDate"),
				"Modificado_Por",c.text(r,"LastUserID"),"Fecha_Mod",c.value(r,"LastModified"));
		});
		// MTA es un valor derivado de la línea base recién importada; no representa una versión legacy independiente.
	}
	private Double temperature(LegacyEtlContext c,Map<String,Object> row,String field,String unit,String inspectionId){
		Object value=row.get(field);if(value==null||value.toString().isBlank())return null;
		try{return TemperatureConverter.toCelsius(value,unit);}catch(TemperatureConverter.UnknownTemperatureUnitException e){
			c.report().unknownTemperatureUnits(1);c.report().error("linea_base");throw new IllegalArgumentException(e.getMessage()+" en "+inspectionId);}
	}
	@SuppressWarnings("unchecked") private String locationPath(LegacyEtlContext c,String id){Object paths=c.shared().get("locationPaths");if(paths instanceof Map<?,?> map&&map.get(id) instanceof LocationHierarchyBuilder.LocationPath path)return path.path();return null;}
	private String key(String a,String b){return a+"\u0000"+b;} private String safe(String v){return v==null?"":v;}
	private boolean inactive(Map<String,Object> row){Object value=row.get("DeleteFlag");return value!=null&&!"0".equals(value.toString());}
}
