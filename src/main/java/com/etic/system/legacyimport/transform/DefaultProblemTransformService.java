package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DefaultProblemTransformService implements ProblemTransformService {

	@Override public void transform(LegacyEtlContext c) {
		Map<String,Map<String,Object>> problems=c.index("problems","ProblemID");
		Map<String,Map<String,Object>> appearances=c.index("problemInspections","ProblemInspectionID");
		Map<String,Map<String,Object>> inspections=c.index("inspections","InspectionID");
		Map<String,Map<String,Object>> equipment=c.index("equipment","EquipmentID");
		Map<String,Map<String,Object>> groups=c.index("equipmentGroups","EquipmentGroupID");
		Map<String,Map<String,Object>> faults=c.index("faults","FaultID");
		Map<String,PhotoSelection> photos=new HashMap<>();
		c.forEach("problemPhotos",r->photos.merge(c.text(r,"ProblemInspectionID"),new PhotoSelection(r,1),(current,ignored)->
			new PhotoSelection(photoKey(c,r).compareTo(photoKey(c,current.photo()))<0?r:current.photo(),current.count()+1)));
		Map<String,String> inspectionBySiteNumber=new HashMap<>();
		inspections.forEach((id,row)->inspectionBySiteNumber.put(key(c.text(row,"CustomerSiteID"),c.text(row,"InspectionNo")),id));
		long[] counts={problems.size(),appearances.size(),0};
		c.transform("pieProblemInspections","problemas","Id_Problema",pie->{
			counts[2]++;
			String pieId=c.text(pie,"PIEProblemInspectionID"), appearanceId=c.text(pie,"ProblemInspectionID");
			Map<String,Object> appearance=appearances.get(appearanceId);
			if(appearance==null)throw new IllegalArgumentException("ProblemInspectionID inexistente: "+appearanceId);
			String problemId=c.text(appearance,"ProblemID"); Map<String,Object> problem=problems.get(problemId);
			if(problem==null)throw new IllegalArgumentException("ProblemID inexistente: "+problemId);
			String inspectionId=c.text(appearance,"InspectionID"); Map<String,Object> inspection=inspections.get(inspectionId);
			if(inspection==null)throw new IllegalArgumentException("InspectionID inexistente en problema: "+inspectionId);
			String unit=c.text(inspection,"TemperatureUnit");
			Double problemTemp=temperature(c,pie,"ProblemTemperature",unit,inspectionId), referenceTemp=temperature(c,pie,"ReferenceTemperature",unit,inspectionId);
			Double ambient=temperature(c,pie,"AmbientTemperature",unit,inspectionId);
			PhotoSelection problemPhotos=photos.get(appearanceId);
			if(problemPhotos!=null&&problemPhotos.count()>1){c.report().additionalPhotos(problemPhotos.count()-1);c.report().warning("problemas","Fotos adicionales para "+appearanceId);}
			Map<String,Object> photo=problemPhotos==null?null:problemPhotos.photo();
			String equipmentId=c.text(problem,"EquipmentID"), faultId=c.text(problem,"FaultID");
			Map<String,Object> equipmentRow=equipment.get(equipmentId), fault=faults.get(faultId);
			Map<String,Object> group=equipmentRow==null?null:groups.get(c.text(equipmentRow,"EquipmentGroupID"));
			String siteId=c.text(inspection,"CustomerSiteID"), closedNo=c.text(problem,"ClosedOnInspectionNo");
			return c.row("Id_Problema",pieId,"Id_Tipo_Inspeccion",c.text(problem,"InspectionTypeID"),"Numero_Problema",c.value(appearance,"ProblemNo"),
				"Id_Sitio",siteId,"Id_Inspeccion",inspectionId,"Id_Inspeccion_Det",c.text(appearance,"InspectionDetailID"),"Id_Ubicacion",c.text(problem,"LocationID"),
				"Problem_Phase",c.text(pie,"ProblemPhaseID"),"Reference_Phase",c.text(pie,"ReferencePhaseID"),"Additional_Info",c.text(pie,"SecondReferencePhaseID"),
				"Problem_Temperature",problemTemp,"Reference_Temperature",referenceTemp,"Temp_Ambient",ambient,
				"Problem_Rms",c.value(pie,"TrueRMSLoad_A"),"Reference_Rms",c.value(pie,"TrueRMSLoad_B"),"Additional_Rms",c.value(pie,"TrueRMSLoad_C"),
				"Environment",c.text(pie,"PIEEnvironmentID"),"Wind_Speed",c.value(pie,"Windspeed"),
				"Ir_File",photo==null?null:c.text(photo,"IRFilename"),"Ir_File_Date",photo==null?null:c.value(photo,"IRDate"),
				"Photo_File",photo==null?null:c.text(photo,"PhotoFileName"),"Photo_File_Date",photo==null?null:c.value(photo,"PhotoDate"),
				"Id_Fabricante",c.text(problem,"ManufacturerID"),"Rated_Load",c.value(problem,"PI4"),"Circuit_Voltage",c.value(problem,"PI5"),
				"Id_Falla",faultId,"Id_Equipo",equipmentId,"Component_Comment",c.text(problem,"ComponentComment"),
				"Estatus_Problema",problemStatus(problem),"Aumento_Temperatura",problemTemp==null||referenceTemp==null?null:problemTemp-referenceTemp,
				"Id_Severidad",c.text(pie,"ProblemSeverityID"),"Estatus",inactive(appearance)?"Inactivo":"Activo","Ruta",locationPath(c,c.text(problem,"LocationID")),
				"hazard_Type",limited(c.text(problem,"FaultType"),38),"hazard_Classification",limited(group==null?null:c.text(group,"Name"),38),
				"hazard_Group",limited(equipmentRow==null?null:c.text(equipmentRow,"Name"),38),"hazard_Issue",limited(fault==null?null:c.text(fault,"Fault"),38),
				"Es_Cronico",booleanText(problem.get("IsChronic")),"Cerrado_En_Inspeccion",closedNo==null?null:inspectionBySiteNumber.get(key(siteId,closedNo)),
				"Creado_Por",c.text(pie,"CreateUserID"),"Fecha_Creacion",c.value(pie,"CreateDate"),"Modificado_Por",c.text(pie,"LastUserID"),"Fecha_Mod",c.value(pie,"LastModified"));
		});
		c.report().legacyProblems(counts[0]);c.report().problemInspections(counts[1]);c.report().pieProblemInspections(counts[2]);c.report().targetProblems(counts[2]);
	}
	private Double temperature(LegacyEtlContext c,Map<String,Object> row,String field,String unit,String inspectionId){Object value=row.get(field);if(value==null||value.toString().isBlank())return null;try{return TemperatureConverter.toCelsius(value,unit);}catch(TemperatureConverter.UnknownTemperatureUnitException e){c.report().unknownTemperatureUnits(1);c.report().error("problemas");throw new IllegalArgumentException(e.getMessage()+" en "+inspectionId);}}
	@SuppressWarnings("unchecked") private String locationPath(LegacyEtlContext c,String id){Object paths=c.shared().get("locationPaths");if(paths instanceof Map<?,?> map&&map.get(id) instanceof LocationHierarchyBuilder.LocationPath path)return path.path();return null;}
	private String problemStatus(Map<String,Object> row){Object value=row.get("ProblemStatus");return value==null?null:truth(value)?"Abierto":"Cerrado";}
	private String booleanText(Object value){return value==null?null:truth(value)?"SI":"NO";}
	private boolean truth(Object v){return v instanceof Boolean b?b:v!=null&&("1".equals(v.toString())||"true".equalsIgnoreCase(v.toString()));}
	private boolean inactive(Map<String,Object> row){Object value=row.get("DeleteFlag");return value!=null&&!"0".equals(value.toString());}
	private String limited(String value,int length){return value==null||value.length()<=length?value:value.substring(0,length);}
	private String photoKey(LegacyEtlContext c,Map<String,Object> row){return safe(c.text(row,"CreateDate"))+safe(c.text(row,"ProblemPhotoID"));}
	private String key(String a,String b){return safe(a)+"\u0000"+safe(b);}private String safe(String v){return v==null?"":v;}
	private record PhotoSelection(Map<String,Object> photo,int count){}
}
