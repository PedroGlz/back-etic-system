package com.etic.system.legacyimport.service;

import com.etic.system.legacyimport.model.LegacyHomologation;
import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LegacyHomologationService {
	private final LegacyImportService imports;
	private final LegacyDatasetStore store;
	private final LegacyBatchUpsertRepository repository;

	public LegacyHomologationService(LegacyImportService imports, LegacyDatasetStore store, LegacyBatchUpsertRepository repository) {
		this.imports = imports;
		this.store = store;
		this.repository = repository;
	}

	public LegacyHomologation.Review review(String id, String createdBy) {
		return review(id, createdBy, null);
	}

	public LegacyHomologation.Review review(String id, String createdBy, String requestedInspectionId) {
		Path staged = imports.stagingDirectory(id, createdBy);
		Map<String, Map<String, Object>> customers = index(staged, "customers", "CustomerID");
		Map<String, Map<String, Object>> sites = index(staged, "customerSites", "CustomerSiteID");
		List<Reference> references = references(staged);
		Reference automatic = automaticReference(references);
		Reference reference = requestedInspectionId == null || requestedInspectionId.isBlank() ? automatic : references.stream()
			.filter(value -> same(value.inspectionId(), requestedInspectionId)).findFirst()
			.orElseThrow(() -> new BusinessValidationException("El InspectionID seleccionado no pertenece al staging del job"));
		Map<String, Object> customer = customers.get(reference.customerId());
		Map<String, Object> site = sites.get(reference.siteId());
		if (customer == null || site == null) throw new BusinessValidationException("La inspección de referencia no tiene cliente o sitio legacy válido");

		Set<String> locationIds = new LinkedHashSet<>();
		store.forEach(staged, "inspectionDetails", row -> {
			if (same(reference.inspectionId(), text(row, "InspectionID"))) add(locationIds, text(row, "LocationID"));
		});
		Map<String, String> legacyPriorityNames = labels(staged, "priorityStatus", "PriorityStatusID", "Name");
		Map<String, Map<String, Object>> currentPriorities = rowsById(repository.query(
			"SELECT Id_Tipo_Prioridad, Tipo_Prioridad, Desc_Prioridad, Estatus, is_default FROM tipo_prioridades"), "Id_Tipo_Prioridad");
		Set<String> inactiveIds = new LinkedHashSet<>();
		store.forEach(staged, "locations", row -> {
			if (!contains(locationIds, text(row, "LocationID"))) return;
			String priorityId = text(row, "PriorityStatusID");
			Map<String, Object> current = get(currentPriorities, priorityId);
			if (priorityId != null && (current == null || !"Activo".equalsIgnoreCase(text(current, "Estatus")))) inactiveIds.add(priorityId);
		});

		List<LegacyHomologation.Priority> inactive = inactiveIds.stream()
			.map(priorityId -> new LegacyHomologation.Priority(priorityId, legacyPriorityNames.getOrDefault(key(priorityId), priorityId), get(currentPriorities, priorityId) == null))
			.toList();
		List<LegacyHomologation.PriorityImpact> impacts=priorityImpacts(staged,reference.inspectionId(),inactiveIds);
		List<LegacyHomologation.InspectionOption> inspectionOptions=references.stream()
			.sorted(Comparator.comparing(Reference::number,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Reference::inspectionDate,Comparator.nullsLast(Comparator.reverseOrder())))
			.map(value->new LegacyHomologation.InspectionOption(value.inspectionId(),value.number(),value.inspectionDate(),text(customers.get(value.customerId()),"Name"))).toList();
		return new LegacyHomologation.Review(
			reference.inspectionId(), reference.number(), automatic.inspectionId(), inspectionOptions,
			reference.customerId(), text(customer, "Name"), reference.siteId(), text(site, "SiteName"), inactive,
			priorityScopeErrors(impacts), impacts,
			options(repository.query("SELECT Id_Cliente id, Razon_Social label FROM clientes WHERE Estatus='Activo' ORDER BY Razon_Social"), null),
			priorityOptions(repository.query("SELECT Id_Tipo_Prioridad id, Tipo_Prioridad, Desc_Prioridad, is_default FROM tipo_prioridades WHERE Estatus='Activo' ORDER BY Tipo_Prioridad")),
			options(repository.query("SELECT Id_Grupo_Sitios id, Grupo label, Id_Cliente parentId FROM grupos_sitios WHERE Estatus='Activo' ORDER BY Grupo"), "parentId"),
			options(repository.query("SELECT Id_Sitio id, Sitio label, Id_Grupo_Sitios parentId, Id_Cliente clientId FROM sitios WHERE Estatus='Activo' ORDER BY Sitio"), "parentId")
		);
	}

	public LegacyHomologation.Selection validate(String id, String createdBy, LegacyHomologation.Request request) {
		return validate(id,createdBy,request,createdBy);
	}

	public LegacyHomologation.Selection validate(String id, String createdBy, LegacyHomologation.Request request, String confirmedBy) {
		if (request == null) throw new BusinessValidationException("La homologación es obligatoria");
		LegacyHomologation.Review review = review(id, createdBy, request.inspectionId());
		Path staged=imports.stagingDirectory(id,createdBy);
		validateInspectionLocations(staged,review);
		if (request.automaticSelection() && !same(review.automaticInspectionId(), request.inspectionId()))
			throw new BusinessValidationException("La selección marcada como automática no corresponde a la inspección automática actual");
		String targetCustomerId=required(request.customerId(),"El cliente es obligatorio");
		List<Map<String,Object>> targetCustomer=repository.query("SELECT Id_Cliente, Razon_Social FROM clientes WHERE Id_Cliente=? AND Estatus='Activo'",targetCustomerId);
		if(targetCustomer.isEmpty())throw new BusinessValidationException("El cliente seleccionado no existe o no está activo");
		String legalName = required(request.legalName(), "La razón social homologada es obligatoria");
		if(!same(legalName,text(targetCustomer.getFirst(),"Razon_Social")))throw new BusinessValidationException("La razón social no coincide con el cliente seleccionado");
		GroupSelection group=resolveGroup(targetCustomerId,request);
		SiteSelection site=resolveSite(review,targetCustomerId,request,group.id());

		Map<String, String> supplied = request.priorityMappings() == null ? Map.of() : request.priorityMappings();
		Map<String, String> mappings = new LinkedHashMap<>();
		for (LegacyHomologation.Priority priority : review.inactivePriorities()) {
			String target = supplied.entrySet().stream().filter(entry -> same(entry.getKey(), priority.sourceId()))
				.map(Map.Entry::getValue).findFirst().orElse(null);
			target = required(target, "Falta homologar la prioridad " + priority.sourceLabel());
			if (repository.query("SELECT Id_Tipo_Prioridad FROM tipo_prioridades WHERE Id_Tipo_Prioridad=? AND Estatus='Activo'", target).isEmpty())
				throw new BusinessValidationException("La prioridad sustituta no está activa: " + target);
			mappings.put(priority.sourceId(), target);
		}
		validateFinalPriorities(staged,review.inspectionId(),mappings);
		boolean updateLegalName=false;
		List<LegacyHomologation.PriorityImpact> impacts=priorityImpacts(staged,review.inspectionId(),mappings.keySet());
		if(!impacts.isEmpty()&&!request.forceGlobalPriorityUpdate())throw new BusinessValidationException(priorityScopeErrors(impacts).getFirst());
		boolean moveLocations=!same(review.siteId(),site.id());
		List<String> relationshipInspections=relationshipInspectionIds(staged,review.inspectionId(),group,site,moveLocations);
		boolean relationshipChanges=group.updateCustomer()||site.update()||moveLocations;
		if(relationshipChanges&&!request.confirmRelationshipChanges())throw new BusinessValidationException("Los cambios de cliente, grupo o sitio pueden afectar otras inspecciones. Confirma expresamente las nuevas relaciones para continuar");
		return new LegacyHomologation.Selection(review.inspectionId(), review.inspectionNumber(), request.automaticSelection(), review.customerId(),targetCustomerId, review.customerName(), review.siteId(), legalName, updateLegalName,
			group.id(),group.name(),group.create(),group.updateName(),group.updateCustomer(),site.id(),site.name(),site.update(),moveLocations,request.confirmRelationshipChanges(),relationshipInspections,Map.copyOf(mappings),request.forceGlobalPriorityUpdate(),List.copyOf(impacts),confirmedBy);
	}

	private List<LegacyHomologation.PriorityImpact> priorityImpacts(Path staged,String inspectionId,Set<String> mappedPriorities){
		if(mappedPriorities.isEmpty())return List.of();
		Map<String,Map<String,Object>> locations=index(staged,"locations","LocationID");
		Map<String,Map<String,Object>> inspections=index(staged,"inspections","InspectionID");
		Map<String,String> priorityNames=labels(staged,"priorityStatus","PriorityStatusID","Name");
		Map<String,String> selectedLocations=new LinkedHashMap<>();
		store.forEach(staged,"inspectionDetails",row->{if(same(inspectionId,text(row,"InspectionID"))){Map<String,Object> location=get(locations,text(row,"LocationID"));if(location!=null&&mappedPriorities.stream().anyMatch(value->same(value,text(location,"PriorityStatusID"))))selectedLocations.put(text(row,"LocationID"),text(location,"PriorityStatusID"));}});
		List<LegacyHomologation.PriorityImpact> impacts=new ArrayList<>();
		for(var selected:selectedLocations.entrySet()){
			Set<String> jobIds=new LinkedHashSet<>();
			store.forEach(staged,"inspectionDetails",row->{if(same(selected.getKey(),text(row,"LocationID"))&&!same(inspectionId,text(row,"InspectionID")))add(jobIds,text(row,"InspectionID"));});
			List<String> jobNames=jobIds.stream().map(value->inspectionLabel(inspections.get(value),value)).toList();
			List<Map<String,Object>> external=repository.query("SELECT DISTINCT i.Id_Inspeccion, i.No_Inspeccion, c.Razon_Social, s.Sitio FROM inspecciones_det d JOIN inspecciones i ON i.Id_Inspeccion=d.Id_Inspeccion LEFT JOIN clientes c ON c.Id_Cliente=i.Id_Cliente LEFT JOIN sitios s ON s.Id_Sitio=i.Id_Sitio WHERE d.Id_Ubicacion=? AND i.Id_Inspeccion<>? ORDER BY i.Id_Inspeccion",selected.getKey(),inspectionId);
			List<String> externalIds=external.stream().map(row->text(row,"Id_Inspeccion")).filter(java.util.Objects::nonNull).toList();
			List<String> externalNames=external.stream().map(row->inspectionLabel(row,text(row,"Id_Inspeccion"))).toList();
			Map<String,Object> location=get(locations,selected.getKey());
			if(!jobIds.isEmpty()||!externalIds.isEmpty())impacts.add(new LegacyHomologation.PriorityImpact(selected.getKey(),text(location,"Name"),selected.getValue(),priorityNames.getOrDefault(key(selected.getValue()),selected.getValue()),List.copyOf(jobIds),jobNames,externalIds,externalNames));
		}
		return List.copyOf(impacts);
	}
	private List<String> priorityScopeErrors(List<LegacyHomologation.PriorityImpact> impacts){return impacts.stream().map(impact->{List<String> messages=new ArrayList<>();if(!impact.jobInspectionIds().isEmpty())messages.add("también se utiliza en otra inspección del archivo histórico que estás importando");if(!impact.externalInspectionIds().isEmpty())messages.add("también se utiliza en otra inspección que ya está registrada en ETIC Online");return "La ubicación "+display(impact.locationName(),impact.locationId())+" "+String.join(" y ",messages)+". Si cambias su prioridad, el nuevo valor se reflejará en todas ellas.";}).toList();}

	private String inspectionLabel(Map<String,Object> row,String id){if(row==null)return id;String number=text(row,"InspectionNo");if(number==null)number=text(row,"No_Inspeccion");String customer=text(row,"Razon_Social"),site=text(row,"Sitio");List<String> values=new ArrayList<>();if(number!=null)values.add("Inspección #"+number);if(customer!=null)values.add(customer);if(site!=null)values.add(site);values.add("ID "+id);return String.join(" · ",values);}
	private String display(String name,String id){return name==null||name.isBlank()?"sin nombre disponible":name;}

	private void validateInspectionLocations(Path staged,LegacyHomologation.Review review){
		Map<String,Map<String,Object>> locations=index(staged,"locations","LocationID");
		store.forEach(staged,"inspectionDetails",detail->{if(!same(review.inspectionId(),text(detail,"InspectionID")))return;String locationId=text(detail,"LocationID");Map<String,Object> location=get(locations,locationId);if(location==null||!same(review.siteId(),text(location,"CustomerSiteID")))throw new BusinessValidationException("La ubicación "+locationId+" no pertenece al sitio histórico de la inspección seleccionada");});
	}

	private void validateFinalPriorities(Path staged,String inspectionId,Map<String,String> mappings){
		Set<String> selectedLocations=new LinkedHashSet<>();
		store.forEach(staged,"inspectionDetails",detail->{if(same(inspectionId,text(detail,"InspectionID")))add(selectedLocations,text(detail,"LocationID"));});
		Map<String,Set<String>> finalPriorities=new LinkedHashMap<>();
		store.forEach(staged,"locations",location->{String locationId=text(location,"LocationID"),source=text(location,"PriorityStatusID");if(!contains(selectedLocations,locationId)||source==null)return;String target=mappings.entrySet().stream().filter(entry->same(entry.getKey(),source)).map(Map.Entry::getValue).findFirst().orElse(source);finalPriorities.computeIfAbsent(locationId,ignored->new LinkedHashSet<>()).add(target);});
		finalPriorities.forEach((locationId,priorities)->{if(priorities.size()>1)throw new BusinessValidationException("La ubicación "+locationId+" tiene prioridades diferentes en el archivo histórico. Selecciona una misma prioridad final para todas sus apariciones");});
	}
	private List<String> relationshipInspectionIds(Path staged,String inspectionId,GroupSelection group,SiteSelection site,boolean moveLocations){
		Set<String> ids=new LinkedHashSet<>();add(ids,inspectionId);
		if(group.updateCustomer())repository.query("SELECT Id_Inspeccion FROM inspecciones WHERE Id_Grupo_Sitios=?",group.id()).forEach(row->add(ids,text(row,"Id_Inspeccion")));
		if(site.update())repository.query("SELECT Id_Inspeccion FROM inspecciones WHERE Id_Sitio=?",site.id()).forEach(row->add(ids,text(row,"Id_Inspeccion")));
		if(moveLocations){Set<String> locations=new LinkedHashSet<>();store.forEach(staged,"inspectionDetails",detail->{if(same(inspectionId,text(detail,"InspectionID")))add(locations,text(detail,"LocationID"));});for(String locationId:locations)repository.query("SELECT DISTINCT Id_Inspeccion FROM inspecciones_det WHERE Id_Ubicacion=?",locationId).forEach(row->add(ids,text(row,"Id_Inspeccion")));}
		return List.copyOf(ids);
	}

	private GroupSelection resolveGroup(String customerId,LegacyHomologation.Request request){
		if(request.siteGroupId()!=null&&!request.siteGroupId().isBlank()){
			List<Map<String,Object>> rows=repository.query("SELECT Id_Grupo_Sitios, Id_Cliente, Grupo FROM grupos_sitios WHERE Id_Grupo_Sitios=? AND Estatus='Activo'",request.siteGroupId().trim());
			if(rows.isEmpty())throw new BusinessValidationException("El grupo seleccionado no existe o no está activo");
			return new GroupSelection(request.siteGroupId().trim(),text(rows.getFirst(),"Grupo"),false,false,!same(customerId,text(rows.getFirst(),"Id_Cliente")));
		}
		String name=required(request.siteGroupName(),"El grupo de sitios existente o nuevo es obligatorio");
		List<Map<String,Object>> groups=repository.query("SELECT Id_Grupo_Sitios, Grupo, Estatus FROM grupos_sitios WHERE Id_Cliente=?",customerId);
		for(Map<String,Object> row:groups)if(normalized(name).equals(normalized(text(row,"Grupo")))){
			if(!"Activo".equalsIgnoreCase(text(row,"Estatus")))throw new BusinessValidationException("Ya existe un grupo equivalente inactivo; debe resolverse fuera de la importación histórica");
			return new GroupSelection(text(row,"Id_Grupo_Sitios"),text(row,"Grupo"),false,false,false);
		}
		String deterministic=deterministicGroupId(customerId);
		List<Map<String,Object>> current=repository.query("SELECT Id_Grupo_Sitios, Id_Cliente, Grupo, Estatus FROM grupos_sitios WHERE Id_Grupo_Sitios=?",deterministic);
		if(current.isEmpty())return new GroupSelection(deterministic,name,true,false,false);
		Map<String,Object> row=current.getFirst();
		if(!same(customerId,text(row,"Id_Cliente")))throw new BusinessValidationException("No se puede usar el grupo propuesto porque está relacionado con otro cliente");
		if(!"Activo".equalsIgnoreCase(text(row,"Estatus")))throw new BusinessValidationException("El grupo propuesto ya existe, pero está inactivo. Selecciona otro grupo o reactívalo primero");
		if(normalized(name).equals(normalized(text(row,"Grupo"))))return new GroupSelection(deterministic,text(row,"Grupo"),false,false,false);
		if(!repository.query("SELECT Id_Sitio FROM sitios WHERE Id_Grupo_Sitios=? LIMIT 1",deterministic).isEmpty()||!repository.query("SELECT Id_Inspeccion FROM inspecciones WHERE Id_Grupo_Sitios=? LIMIT 1",deterministic).isEmpty())
			throw new BusinessValidationException("El grupo propuesto ya se utiliza en otros registros y no puede cambiarse desde esta importación");
		return new GroupSelection(deterministic,name,false,true,false);
	}

	private SiteSelection resolveSite(LegacyHomologation.Review review,String customerId,LegacyHomologation.Request request,String groupId){
		if(request.siteId()!=null&&!request.siteId().isBlank()){
			List<Map<String,Object>> rows=repository.query("SELECT Id_Sitio, Id_Cliente, Id_Grupo_Sitios, Sitio FROM sitios WHERE Id_Sitio=? AND Estatus='Activo'",request.siteId().trim());
			if(rows.isEmpty())throw new BusinessValidationException("El sitio seleccionado no existe o no está activo");
			Map<String,Object> row=rows.getFirst();
			return new SiteSelection(request.siteId().trim(),text(row,"Sitio"),!same(customerId,text(row,"Id_Cliente"))||!same(groupId,text(row,"Id_Grupo_Sitios")));
		}
		String name=required(request.siteName(),"El sitio existente o nuevo es obligatorio");
		String historicalId=review.siteId();
		for(Map<String,Object> equivalent:repository.query("SELECT Id_Sitio, Sitio FROM sitios WHERE Id_Cliente=? AND Id_Grupo_Sitios=?",customerId,groupId))if(normalized(name).equals(normalized(text(equivalent,"Sitio")))&&!same(historicalId,text(equivalent,"Id_Sitio")))
			throw new BusinessValidationException("Ya existe un sitio con ese nombre, pero corresponde a otro registro. No se sustituirá la identidad del sitio histórico");
		List<Map<String,Object>> current=repository.query("SELECT Id_Sitio, Id_Cliente, Id_Grupo_Sitios, Sitio FROM sitios WHERE Id_Sitio=?",historicalId);
		if(current.isEmpty())return new SiteSelection(historicalId,name,true);
		Map<String,Object> row=current.getFirst();
		boolean changes=!same(customerId,text(row,"Id_Cliente"))||!same(groupId,text(row,"Id_Grupo_Sitios"))||!normalized(name).equals(normalized(text(row,"Sitio")));
		return new SiteSelection(historicalId,name,changes);
	}

	private List<Reference> references(Path staged) {
		List<Reference> candidates = new ArrayList<>();
		store.forEach(staged, "inspections", row -> {
			Long number = inspectionNumber(row.get("InspectionNo"));
			String inspectionId=text(row,"InspectionID");
			if(inspectionId!=null)candidates.add(new Reference(inspectionId,number,first(text(row,"ScheduledStart"),text(row,"ScheduledEnd")),text(row,"CustomerID"),text(row,"CustomerSiteID")));
		});
		if(candidates.isEmpty())throw new BusinessValidationException("El staging no contiene inspecciones con InspectionID válido");
		return candidates;
	}

	private Reference automaticReference(List<Reference> candidates){
		return candidates.stream().filter(value->value.number()!=null).max(Comparator.comparingLong(Reference::number))
			.orElseThrow(() -> new BusinessValidationException("Ningún número de inspección legacy puede interpretarse como entero"));
	}

	private Long inspectionNumber(Object value) {
		if (value == null) return null;
		try { return new BigDecimal(value.toString().trim()).longValueExact(); }
		catch (ArithmeticException | NumberFormatException ignored) { return null; }
	}
	private Map<String, Map<String, Object>> index(Path staged, String dataset, String idField) { Map<String, Map<String, Object>> result=new LinkedHashMap<>();store.forEach(staged,dataset,row->{String id=text(row,idField);if(id!=null)result.put(id,row);});return result; }
	private Map<String,String> labels(Path staged,String dataset,String idField,String labelField){Map<String,String> result=new LinkedHashMap<>();store.forEach(staged,dataset,row->{String id=text(row,idField);if(id!=null)result.put(key(id),text(row,labelField));});return result;}
	private Map<String,Map<String,Object>> rowsById(List<Map<String,Object>> rows,String idField){Map<String,Map<String,Object>> result=new LinkedHashMap<>();rows.forEach(row->{String id=text(row,idField);if(id!=null)result.put(key(id),row);});return result;}
	private List<LegacyHomologation.Option> options(List<Map<String,Object>> rows,String parentField){return rows.stream().map(row->new LegacyHomologation.Option(text(row,"id"),text(row,"label"),parentField==null?null:text(row,parentField),text(row,"clientId"),false)).toList();}
	private List<LegacyHomologation.Option> priorityOptions(List<Map<String,Object>> rows){return rows.stream().map(row->{String code=text(row,"Tipo_Prioridad"),description=text(row,"Desc_Prioridad");String label=description==null?code:code+" - "+description;return new LegacyHomologation.Option(text(row,"id"),label,null,null,booleanValue(row.get("is_default")));}).toList();}
	private Map<String,Object> get(Map<String,Map<String,Object>> rows,String id){return id==null?null:rows.get(key(id));}
	private boolean contains(Set<String> values,String value){return value!=null&&values.stream().anyMatch(item->same(item,value));}
	private void add(Set<String> values,String value){if(value!=null)values.add(value);}
	private String required(String value,String message){if(value==null||value.isBlank())throw new BusinessValidationException(message);return value.trim();}
	private String first(String... values){for(String value:values)if(value!=null&&!value.isBlank())return value;return null;}
	private String text(Map<String,Object> row,String field){if(row==null)return null;Object value=row.entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(field)).map(Map.Entry::getValue).findFirst().orElse(null);return value==null||value.toString().isBlank()?null:value.toString().trim();}
	private boolean same(String left,String right){return left!=null&&right!=null&&left.equalsIgnoreCase(right);}
	private String key(String value){return value.toLowerCase(Locale.ROOT);}
	private boolean booleanValue(Object value){return value instanceof Boolean bool?bool:value!=null&&(value.toString().equals("1")||value.toString().equalsIgnoreCase("true"));}
	private String normalized(String value){return Normalizer.normalize(value.trim().replaceAll("\\s+"," "),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
	private String deterministicGroupId(String customerId){return UUID.nameUUIDFromBytes(("ETIC:LEGACY:GROUP:"+customerId).getBytes(StandardCharsets.UTF_8)).toString().toUpperCase(Locale.ROOT);}
	private record GroupSelection(String id,String name,boolean create,boolean updateName,boolean updateCustomer){}
	private record SiteSelection(String id,String name,boolean update){}
	private record Reference(String inspectionId,Long number,String inspectionDate,String customerId,String siteId){}
}
