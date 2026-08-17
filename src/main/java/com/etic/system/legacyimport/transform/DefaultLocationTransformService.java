package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DefaultLocationTransformService implements LocationTransformService {

	private final LocationHierarchyBuilder hierarchyBuilder = new LocationHierarchyBuilder();

	@Override public void transform(LegacyEtlContext c) {
		Map<String,Map<String,Object>> locations=c.index("locations","LocationID");
		Map<String,LocationHierarchyBuilder.LocationNode> nodes=new LinkedHashMap<>();
		locations.forEach((id,row)->nodes.put(id,new LocationHierarchyBuilder.LocationNode(id,c.text(row,"CustomerSiteID"),c.text(row,"ParentID"),c.text(row,"Name"))));
		Map<String,LocationHierarchyBuilder.LocationPath> paths=hierarchyBuilder.build(nodes);
		Map<String,Map<String,Object>> equipment=c.index("equipment","EquipmentID");
		c.transform("locations","ubicaciones","Id_Ubicacion",r->{
			String id=c.text(r,"LocationID"), equipmentId=c.text(r,"EquipmentID");
			Map<String,Object> equipmentRow=equipment.get(equipmentId);
			LocationHierarchyBuilder.LocationPath path=paths.get(id);
			String legacyPath=c.text(r,"LocationPath");
			if(legacyPath!=null && path!=null && !normalize(legacyPath).endsWith(normalize(path.path())))
				c.report().warning("ubicaciones","LocationPath difiere de la ruta reconstruida: "+id);
			return c.row("Id_Ubicacion",id,"Id_Sitio",c.text(r,"CustomerSiteID"),"Id_Ubicacion_padre",c.text(r,"ParentID")==null?"0":c.text(r,"ParentID"),
				"Id_Tipo_Prioridad",c.text(r,"PriorityStatusID"),"Id_Tipo_Inspeccion",c.text(r,"InspectionTypeID"),
				"Ubicacion",c.text(r,"Name"),"Descripcion",c.text(r,"Description"),"Es_Equipo",booleanText(r.get("IsEquipment")),
				"Codigo_Barras",c.text(r,"BarCode"),"Nivel_arbol",path==null?null:path.level(),"LIMITE",c.value(r,"Threshold"),
				"Fabricante",c.text(r,"ManufacturerID"),"Nombre_Foto",c.text(r,"PhotoFilename"),"Ruta",path==null?null:path.path(),
				"Orden_Arbol",c.value(r,"InspectionOrder"),"Id_Categoria_Equipo",equipmentRow==null?null:c.text(equipmentRow,"EquipmentGroupID"),
				"Estatus",inactive(r)?"Inactivo":"Activo","Creado_Por",c.text(r,"CreateUserID"),"Fecha_Creacion",c.value(r,"CreateDate"),
				"Modificado_Por",c.text(r,"LastUserID"),"Fecha_Mod",c.value(r,"LastModified"));
		});
		c.shared().put("locationPaths",paths);
	}
	private String booleanText(Object value){return value==null?null:booleanValue(value)?"SI":"NO";}
	private boolean booleanValue(Object value){ return value instanceof Boolean b?b:value!=null && ("1".equals(value.toString())||"true".equalsIgnoreCase(value.toString())); }
	private boolean inactive(Map<String,Object> row){ Object value=row.get("DeleteFlag"); return value!=null&&!"0".equals(value.toString()); }
	private String normalize(String value){return value.replace('\\','/').replaceAll("^/+|/+$","").toLowerCase();}
}
