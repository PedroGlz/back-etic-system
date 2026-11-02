package com.etic.system.legacyimport.service;

import com.etic.system.legacyimport.model.LegacyHomologation;
import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
			"SELECT Id_Tipo_Prioridad, Tipo_Prioridad, Estatus FROM tipo_prioridades"), "Id_Tipo_Prioridad");
		Set<String> inactiveIds = new LinkedHashSet<>();
		store.forEach(staged, "locations", row -> {
			if (!contains(locationIds, text(row, "LocationID"))) return;
			String priorityId = text(row, "PriorityStatusID");
			Map<String, Object> current = get(currentPriorities, priorityId);
			if (priorityId != null && current != null && !"Activo".equalsIgnoreCase(text(current, "Estatus"))) inactiveIds.add(priorityId);
		});

		List<LegacyHomologation.Priority> inactive = inactiveIds.stream()
			.map(priorityId -> new LegacyHomologation.Priority(priorityId, legacyPriorityNames.getOrDefault(key(priorityId), priorityId)))
			.toList();
		List<LegacyHomologation.InspectionOption> inspectionOptions=references.stream()
			.sorted(Comparator.comparing(Reference::number,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Reference::inspectionDate,Comparator.nullsLast(Comparator.reverseOrder())))
			.map(value->new LegacyHomologation.InspectionOption(value.inspectionId(),value.number(),value.inspectionDate(),text(customers.get(value.customerId()),"Name"))).toList();
		return new LegacyHomologation.Review(
			reference.inspectionId(), reference.number(), automatic.inspectionId(), inspectionOptions,
			reference.customerId(), text(customer, "Name"), reference.siteId(), text(site, "SiteName"), inactive,
			priorityScopeErrors(staged,reference.inspectionId(),inactiveIds),
			options(repository.query("SELECT Id_Cliente id, Razon_Social label FROM clientes WHERE Estatus='Activo' ORDER BY Razon_Social"), null),
			options(repository.query("SELECT Id_Tipo_Prioridad id, Tipo_Prioridad label FROM tipo_prioridades WHERE Estatus='Activo' ORDER BY Tipo_Prioridad"), null),
			options(repository.query("SELECT Id_Grupo_Sitios id, Grupo label, Id_Cliente parentId FROM grupos_sitios WHERE Estatus='Activo' ORDER BY Grupo"), "parentId"),
			options(repository.query("SELECT Id_Sitio id, Sitio label, Id_Grupo_Sitios parentId FROM sitios WHERE Estatus='Activo' ORDER BY Sitio"), "parentId")
		);
	}

	public LegacyHomologation.Selection validate(String id, String createdBy, LegacyHomologation.Request request) {
		if (request == null) throw new BusinessValidationException("La homologación es obligatoria");
		LegacyHomologation.Review review = review(id, createdBy, request.inspectionId());
		if (request.automaticSelection() && !same(review.automaticInspectionId(), request.inspectionId()))
			throw new BusinessValidationException("La selección marcada como automática no corresponde a la inspección automática actual");
		String legalName = required(request.legalName(), "La razón social homologada es obligatoria");
		String groupId = required(request.siteGroupId(), "El grupo de sitios es obligatorio");
		String siteId = required(request.siteId(), "El sitio es obligatorio");
		List<Map<String, Object>> relationship = repository.query(
			"SELECT s.Id_Sitio FROM sitios s JOIN grupos_sitios g ON g.Id_Grupo_Sitios=s.Id_Grupo_Sitios " +
				"WHERE s.Id_Sitio=? AND s.Id_Grupo_Sitios=? AND s.Id_Cliente=? AND g.Id_Cliente=? " +
				"AND s.Estatus='Activo' AND g.Estatus='Activo'", siteId, groupId, review.customerId(), review.customerId());
		if (relationship.isEmpty()) throw new BusinessValidationException("El sitio no pertenece al grupo y cliente de la inspección histórica");

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
		List<Map<String,Object>> currentCustomer=repository.query("SELECT Razon_Social FROM clientes WHERE Id_Cliente=?",review.customerId());
		boolean updateLegalName=currentCustomer.isEmpty()&&!same(legalName,review.customerName());
		if(!currentCustomer.isEmpty()&&!same(legalName,text(currentCustomer.getFirst(),"Razon_Social")))
			throw new BusinessValidationException("La razón social pertenece a un cliente global existente y no puede modificarse desde una importación histórica");
		List<String> blockers=priorityScopeErrors(imports.stagingDirectory(id,createdBy),review.inspectionId(),mappings.keySet());
		if(!blockers.isEmpty())throw new BusinessValidationException(blockers.getFirst());
		return new LegacyHomologation.Selection(review.inspectionId(), review.inspectionNumber(), request.automaticSelection(), review.customerId(), review.customerName(), review.siteId(), legalName, updateLegalName, groupId, siteId, Map.copyOf(mappings));
	}

	private List<String> priorityScopeErrors(Path staged,String inspectionId,Set<String> mappedPriorities){
		if(mappedPriorities.isEmpty())return List.of();
		List<String> errors=new ArrayList<>();
		Map<String,Map<String,Object>> locations=index(staged,"locations","LocationID");
		Set<String> selectedLocations=new LinkedHashSet<>();
		store.forEach(staged,"inspectionDetails",row->{if(same(inspectionId,text(row,"InspectionID"))){Map<String,Object> location=get(locations,text(row,"LocationID"));if(location!=null&&mappedPriorities.stream().anyMatch(value->same(value,text(location,"PriorityStatusID"))))add(selectedLocations,text(row,"LocationID"));}});
		store.forEach(staged,"inspectionDetails",row->{String locationId=text(row,"LocationID");if(contains(selectedLocations,locationId)&&!same(inspectionId,text(row,"InspectionID")))errors.add("La ubicación "+locationId+" también está vinculada a otra inspección del respaldo; su prioridad no puede actualizarse globalmente.");});
		for(String locationId:selectedLocations)if(!repository.query("SELECT Id_Inspeccion_Det FROM inspecciones_det WHERE Id_Ubicacion=? AND Id_Inspeccion<>? LIMIT 1",locationId,inspectionId).isEmpty())errors.add("La ubicación "+locationId+" está vinculada a una inspección externa al job; su prioridad no puede actualizarse globalmente.");
		return errors.stream().distinct().toList();
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
	private List<LegacyHomologation.Option> options(List<Map<String,Object>> rows,String parentField){return rows.stream().map(row->new LegacyHomologation.Option(text(row,"id"),text(row,"label"),parentField==null?null:text(row,parentField))).toList();}
	private Map<String,Object> get(Map<String,Map<String,Object>> rows,String id){return id==null?null:rows.get(key(id));}
	private boolean contains(Set<String> values,String value){return value!=null&&values.stream().anyMatch(item->same(item,value));}
	private void add(Set<String> values,String value){if(value!=null)values.add(value);}
	private String required(String value,String message){if(value==null||value.isBlank())throw new BusinessValidationException(message);return value.trim();}
	private String first(String... values){for(String value:values)if(value!=null&&!value.isBlank())return value;return null;}
	private String text(Map<String,Object> row,String field){if(row==null)return null;Object value=row.entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(field)).map(Map.Entry::getValue).findFirst().orElse(null);return value==null||value.toString().isBlank()?null:value.toString().trim();}
	private boolean same(String left,String right){return left!=null&&right!=null&&left.equalsIgnoreCase(right);}
	private String key(String value){return value.toLowerCase(Locale.ROOT);}
	private record Reference(String inspectionId,Long number,String inspectionDate,String customerId,String siteId){}
}
