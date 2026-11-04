package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.model.LegacyHomologation;
import com.etic.system.legacyimport.model.ValidationSeverity;
import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.report.LegacyEtlReport;
import com.etic.system.legacyimport.report.LegacyEtlReportBuilder;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import com.etic.system.legacyimport.repository.LegacyImportJobRepository;
import com.etic.system.legacyimport.transform.BaselineTransformService;
import com.etic.system.legacyimport.transform.CatalogTransformService;
import com.etic.system.legacyimport.transform.ChronicHistoryTransformService;
import com.etic.system.legacyimport.transform.CustomerTransformService;
import com.etic.system.legacyimport.transform.InspectionTransformService;
import com.etic.system.legacyimport.transform.LegacyEtlContext;
import com.etic.system.legacyimport.transform.LocationTransformService;
import com.etic.system.legacyimport.transform.ProblemTransformService;
import com.etic.system.legacyimport.transform.SiteTransformService;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import com.etic.system.legacyimport.validator.LegacyResultValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LegacyEtlExecutionService {
	private static final Logger log=LoggerFactory.getLogger(LegacyEtlExecutionService.class);

	private final LegacyImportJobRepository jobs; private final LegacyImportService imports;
	private final LegacyDatasetStore store; private final LegacyBatchUpsertRepository batchRepository;
	private final LegacyImportProperties properties; private final TransactionTemplate transactions; private final ObjectMapper mapper;
	private final CatalogTransformService catalogs; private final CustomerTransformService customers; private final SiteTransformService sites;
	private final LocationTransformService locations; private final InspectionTransformService inspections; private final BaselineTransformService baselines;
	private final ProblemTransformService problems; private final ChronicHistoryTransformService chronicHistory; private final Path baseDirectory;
	private final LegacyResultValidationService finalValidation;
	private final TaskExecutor taskExecutor;

	public LegacyEtlExecutionService(LegacyImportJobRepository jobs,LegacyImportService imports,LegacyDatasetStore store,
		LegacyBatchUpsertRepository batchRepository,LegacyImportProperties properties,TransactionTemplate transactions,ObjectMapper mapper,
		CatalogTransformService catalogs,CustomerTransformService customers,SiteTransformService sites,LocationTransformService locations,
		InspectionTransformService inspections,BaselineTransformService baselines,ProblemTransformService problems,
		ChronicHistoryTransformService chronicHistory,LegacyResultValidationService finalValidation,StorageProperties storageProperties,TaskExecutor taskExecutor){
		this.jobs=jobs;this.imports=imports;this.store=store;this.batchRepository=batchRepository;this.properties=properties;this.transactions=transactions;this.mapper=mapper;
		this.catalogs=catalogs;this.customers=customers;this.sites=sites;this.locations=locations;this.inspections=inspections;this.baselines=baselines;this.problems=problems;this.chronicHistory=chronicHistory;
		this.finalValidation=finalValidation;
		this.taskExecutor=taskExecutor;
		this.baseDirectory=storageProperties.basePath().resolve("legacy-imports").normalize();
	}

	public LegacyEtlReport execute(String id,String createdBy){
		LegacyImportJob job=claim(id,createdBy);
		return executeClaimed(job,null);
	}

	public void start(String id,String createdBy){
		start(id,createdBy,null);
	}

	public void start(String id,String createdBy,LegacyHomologation.Selection selection){
		LegacyImportJob job=claim(id,createdBy);
		try{taskExecutor.execute(()->{try{executeClaimed(job,selection);}catch(RuntimeException exception){log.warn("LEGACY IMPORT - job {} finalizó con error: {}",id,rootMessage(exception));}});}
		catch(RuntimeException exception){jobs.updateState(id,LegacyImportStatus.FAILED,"VALIDATION",100,"No fue posible iniciar la ejecución",LocalDateTime.now());throw new BusinessValidationException("No fue posible iniciar la importación");}
	}

	private LegacyImportJob claim(String id,String createdBy){
		LegacyImportJob job=jobs.findById(id).orElseThrow(()->new ResourceNotFoundException("Importación legacy no encontrada"));
		if(job.status()!=LegacyImportStatus.READY&&job.status()!=LegacyImportStatus.COMPLETED)throw new BusinessValidationException("La importación no está lista para ejecutarse");
		if(createdBy==null||job.createdBy()==null||!job.createdBy().equalsIgnoreCase(createdBy))throw new ResourceNotFoundException("Importación legacy no encontrada");
		Path staged=baseDirectory.resolve(id+"-datasets").normalize();
		if(!staged.startsWith(baseDirectory)||!store.isReady(staged))throw new ResourceNotFoundException("Staging legacy no encontrado");
		if(!jobs.claimForExecution(id,createdBy))throw new BusinessValidationException("La importación ya está en proceso o no está lista para ejecutarse");
		return job;
	}

	private LegacyEtlReport executeClaimed(LegacyImportJob job,LegacyHomologation.Selection selection){
		String id=job.id();
		Path staged=baseDirectory.resolve(id+"-datasets").normalize();
		LegacyEtlReportBuilder report=new LegacyEtlReportBuilder(id);
		try{
			jobs.updateState(id,LegacyImportStatus.PROCESSING,"VALIDATION",2,null,null);
			LegacyImportAnalysis analysis=imports.analysisForExecution(id);
			if(analysis.alerts().stream().anyMatch(issue->issue.severity()==ValidationSeverity.ERROR))throw new BusinessValidationException("El análisis contiene errores estructurales o relaciones huérfanas");
			LegacyEtlContext context=new LegacyEtlContext(id,staged,store,batchRepository,report,properties.getBatchSize());
			phase(id,"CATALOGS",10,context,report,()->catalogs.transform(context));
			context.release("inspectionStatuses","inspectionDetailStatuses","inspectionTypes","priorityStatus","faultTypes","piePhases","pieEnvironments","problemSeverity","manufacturers","rootCauses","equipmentFaultLinks");
			phase(id,"CUSTOMERS",20,context,report,()->customers.transform(context));context.release("customers");
			phase(id,"SITES",30,context,report,()->sites.transform(context));context.release("customerSites");
			phase(id,"LOCATIONS",42,context,report,()->locations.transform(context));
			phase(id,"INSPECTIONS",55,context,report,()->{inspections.transform(context);if(selection!=null){context.flushPending();applyHomologation(context,selection);}});context.releaseShared("groupByCustomer");
			phase(id,"BASELINES",68,context,report,()->baselines.transform(context));context.release("locationBaselines","locationBaselinePhotos","inspectionDetails");
			phase(id,"PROBLEMS",82,context,report,()->problems.transform(context));context.release("problemPhotos","equipment","equipmentGroups","faults","locations");context.releaseShared("locationPaths");
			phase(id,"CHRONIC_HISTORY",93,context,report,()->{chronicHistory.transform(context);if(selection!=null&&selection.moveLocationsToSite())applyRelatedSiteConsistency(selection);});context.releaseAll();
			jobs.updateState(id,LegacyImportStatus.VALIDATING_RESULT,"VALIDATION",97,null,null);
			finalValidation.validate(report);
			LegacyEtlReport result=report.build();LocalDateTime finishedAt=LocalDateTime.now();writeArtifacts(id,result,"COMPLETED",null,job,finishedAt);
			jobs.updateState(id,LegacyImportStatus.COMPLETED,"VALIDATION",100,null,finishedAt);
			return result;
		}catch(Exception exception){LocalDateTime finishedAt=LocalDateTime.now();try{writeArtifacts(id,report.build(),"FAILED",rootMessage(exception),job,finishedAt);}catch(IOException ignored){}jobs.updateState(id,LegacyImportStatus.FAILED,"VALIDATION",100,rootMessage(exception),finishedAt);
			if(exception instanceof BusinessValidationException validation)throw validation;throw new BusinessValidationException(rootMessage(exception));}
	}

	private void applyHomologation(LegacyEtlContext context,LegacyHomologation.Selection selection){
		for(String target:selection.priorityMappings().values())if(batchRepository.query("SELECT Id_Tipo_Prioridad FROM tipo_prioridades WHERE Id_Tipo_Prioridad=? AND Estatus='Activo'",target).isEmpty())
			throw new BusinessValidationException("La prioridad sustituta dejó de estar activa antes de aplicar la homologación: "+target);
		if(selection.createSiteGroup()&&batchRepository.query("SELECT Id_Grupo_Sitios FROM grupos_sitios WHERE Id_Grupo_Sitios=?",selection.siteGroupId()).isEmpty())
			batchRepository.insertDerived("grupos_sitios","Id_Grupo_Sitios",Map.of("Id_Grupo_Sitios",selection.siteGroupId(),"Id_Cliente",selection.customerId(),"Grupo",selection.siteGroupName(),"Estatus","Activo","Creado_Por",selection.confirmedBy()));
		if(selection.createSiteGroup()||selection.updateSiteGroupName())batchRepository.updateDerived("grupos_sitios","Id_Grupo_Sitios",List.of(Map.of("Id_Grupo_Sitios",selection.siteGroupId(),"Grupo",selection.siteGroupName())));
		if(selection.updateSiteGroupCustomer()){
			batchRepository.updateDerived("grupos_sitios","Id_Grupo_Sitios",List.of(Map.of("Id_Grupo_Sitios",selection.siteGroupId(),"Id_Cliente",selection.customerId())));
			List<Map<String,Object>> groupSites=batchRepository.query("SELECT Id_Sitio FROM sitios WHERE Id_Grupo_Sitios=?",selection.siteGroupId()).stream().map(row->Map.<String,Object>of("Id_Sitio",row.get("Id_Sitio"),"Id_Cliente",selection.customerId())).toList();
			batchRepository.updateDerived("sitios","Id_Sitio",groupSites);
			List<Map<String,Object>> groupInspections=batchRepository.query("SELECT Id_Inspeccion FROM inspecciones WHERE Id_Grupo_Sitios=?",selection.siteGroupId()).stream().map(row->Map.<String,Object>of("Id_Inspeccion",row.get("Id_Inspeccion"),"Id_Cliente",selection.customerId())).toList();
			batchRepository.updateDerived("inspecciones","Id_Inspeccion",groupInspections);
		}
		if(selection.updateSite())batchRepository.updateDerived("sitios","Id_Sitio",List.of(Map.of("Id_Sitio",selection.siteId(),"Id_Cliente",selection.customerId(),"Id_Grupo_Sitios",selection.siteGroupId(),"Sitio",selection.siteName())));
		if(selection.updateSite()){
			List<Map<String,Object>> siteInspections=batchRepository.query("SELECT Id_Inspeccion FROM inspecciones WHERE Id_Sitio=?",selection.siteId()).stream().map(row->Map.<String,Object>of("Id_Inspeccion",row.get("Id_Inspeccion"),"Id_Cliente",selection.customerId(),"Id_Grupo_Sitios",selection.siteGroupId())).toList();
			batchRepository.updateDerived("inspecciones","Id_Inspeccion",siteInspections);
		}
		batchRepository.updateDerived("inspecciones","Id_Inspeccion",List.of(Map.of(
			"Id_Inspeccion",selection.inspectionId(),"Id_Cliente",selection.customerId(),"Id_Grupo_Sitios",selection.siteGroupId(),"Id_Sitio",selection.siteId())));
		List<Map<String,Object>> detailUpdates=new ArrayList<>();
		Map<String,Map<String,Object>> locations=context.index("locations","LocationID");
		Map<String,Map<String,Object>> locationUpdates=new LinkedHashMap<>();
		Map<String,Map<String,Object>> locationSiteUpdates=new LinkedHashMap<>();
		java.util.Set<String> inspectionsMovedWithLocations=new java.util.LinkedHashSet<>();
		context.forEach("inspectionDetails",detail->{
			if(!selection.inspectionId().equalsIgnoreCase(context.text(detail,"InspectionID")))return;
			detailUpdates.add(Map.of("Id_Inspeccion_Det",context.text(detail,"InspectionDetailID"),"Id_Sitio",selection.siteId()));
			String locationId=context.text(detail,"LocationID");Map<String,Object> location=locations.get(locationId);if(location==null)return;
			if(selection.moveLocationsToSite()){
				locationSiteUpdates.put(locationId,Map.of("Id_Ubicacion",locationId,"Id_Sitio",selection.siteId()));
				batchRepository.query("SELECT DISTINCT Id_Inspeccion FROM inspecciones_det WHERE Id_Ubicacion=?",locationId).forEach(row->inspectionsMovedWithLocations.add(String.valueOf(row.get("Id_Inspeccion"))));
			}
			String source=context.text(location,"PriorityStatusID");String target=selection.priorityMappings().entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(source)).map(Map.Entry::getValue).findFirst().orElse(null);
			if(target!=null)locationUpdates.put(locationId,Map.of("Id_Ubicacion",locationId,"Id_Tipo_Prioridad",target));
		});
		batchRepository.updateDerived("inspecciones_det","Id_Inspeccion_Det",detailUpdates);
		batchRepository.updateDerived("ubicaciones","Id_Ubicacion",new ArrayList<>(locationSiteUpdates.values()));
		if(!inspectionsMovedWithLocations.isEmpty()){
			batchRepository.updateDerived("inspecciones","Id_Inspeccion",inspectionsMovedWithLocations.stream().map(inspectionId->Map.<String,Object>of("Id_Inspeccion",inspectionId,"Id_Cliente",selection.customerId(),"Id_Grupo_Sitios",selection.siteGroupId(),"Id_Sitio",selection.siteId())).toList());
			List<Map<String,Object>> movedDetails=new ArrayList<>();for(String inspectionId:inspectionsMovedWithLocations)batchRepository.query("SELECT Id_Inspeccion_Det FROM inspecciones_det WHERE Id_Inspeccion=?",inspectionId).forEach(row->movedDetails.add(Map.of("Id_Inspeccion_Det",row.get("Id_Inspeccion_Det"),"Id_Sitio",selection.siteId())));batchRepository.updateDerived("inspecciones_det","Id_Inspeccion_Det",movedDetails);
		}
		batchRepository.updateDerived("ubicaciones","Id_Ubicacion",new ArrayList<>(locationUpdates.values()));
		if(selection.updateLegalName())batchRepository.updateDerived("clientes","Id_Cliente",List.of(Map.of("Id_Cliente",selection.customerId(),"Razon_Social",selection.legalName())));
		context.report().warning("homologacion","InspectionID "+selection.inspectionId()+", InspectionNo "+selection.inspectionNumber()+", selección "+(selection.automaticSelection()?"automática":"manual")+": razón social '"+selection.sourceLegalName()+"' → '"+selection.legalName()+"', sitio "+selection.sourceSiteId()+" → "+selection.siteId()+", grupo "+selection.siteGroupId()+", prioridades de ubicaciones "+selection.priorityMappings());
		if(selection.relationshipChangesConfirmed())context.report().warning("homologacion","Usuario "+selection.confirmedBy()+" confirmó relaciones: cliente '"+selection.sourceLegalName()+"' ("+selection.sourceCustomerId()+") → '"+selection.legalName()+"' ("+selection.customerId()+"), sitio "+selection.sourceSiteId()+" → '"+selection.siteName()+"' ("+selection.siteId()+"), grupo '"+selection.siteGroupName()+"' ("+selection.siteGroupId()+"), inspecciones relacionadas "+selection.relationshipInspectionIds());
		for(LegacyHomologation.PriorityImpact impact:selection.priorityImpacts()){
			String target=selection.priorityMappings().entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(impact.sourcePriorityId())).map(Map.Entry::getValue).findFirst().orElse(impact.sourcePriorityId());
			String targetName=batchRepository.query("SELECT Tipo_Prioridad, Desc_Prioridad FROM tipo_prioridades WHERE Id_Tipo_Prioridad=?",target).stream().findFirst().map(row->{String code=String.valueOf(row.get("Tipo_Prioridad")),description=row.get("Desc_Prioridad")==null?null:String.valueOf(row.get("Desc_Prioridad"));return description==null||description.isBlank()?code:code+" - "+description;}).orElse(target);
			context.report().warning("homologacion","Usuario "+selection.confirmedBy()+", ubicación '"+impact.locationName()+"' ("+impact.locationId()+"), prioridad '"+impact.sourcePriorityName()+"' ("+impact.sourcePriorityId()+") → '"+targetName+"' ("+target+"), otras inspecciones del archivo "+impact.jobInspectionNames()+", inspecciones registradas en ETIC Online "+impact.externalInspectionNames()+", cambio compartido confirmado="+selection.forceGlobalPriorityUpdate());
		}
	}
	private void applyRelatedSiteConsistency(LegacyHomologation.Selection selection){
		java.util.Set<String> inspectionIds=new java.util.LinkedHashSet<>();inspectionIds.add(selection.inspectionId());
		java.util.Set<String> locationIds=new java.util.LinkedHashSet<>();for(String inspectionId:List.copyOf(inspectionIds))batchRepository.query("SELECT DISTINCT Id_Ubicacion FROM inspecciones_det WHERE Id_Inspeccion=?",inspectionId).forEach(row->{Object value=row.get("Id_Ubicacion");if(value!=null)locationIds.add(value.toString());});
		for(String locationId:locationIds)batchRepository.query("SELECT DISTINCT Id_Inspeccion FROM inspecciones_det WHERE Id_Ubicacion=?",locationId).forEach(row->{Object value=row.get("Id_Inspeccion");if(value!=null)inspectionIds.add(value.toString());});
		batchRepository.updateDerived("ubicaciones","Id_Ubicacion",locationIds.stream().map(locationId->Map.<String,Object>of("Id_Ubicacion",locationId,"Id_Sitio",selection.siteId())).toList());
		batchRepository.updateDerived("inspecciones","Id_Inspeccion",inspectionIds.stream().map(inspectionId->Map.<String,Object>of("Id_Inspeccion",inspectionId,"Id_Cliente",selection.customerId(),"Id_Grupo_Sitios",selection.siteGroupId(),"Id_Sitio",selection.siteId())).toList());
		List<Map<String,Object>> details=new ArrayList<>(),problems=new ArrayList<>(),baselines=new ArrayList<>();for(String inspectionId:inspectionIds){batchRepository.query("SELECT Id_Inspeccion_Det FROM inspecciones_det WHERE Id_Inspeccion=?",inspectionId).forEach(row->details.add(Map.of("Id_Inspeccion_Det",row.get("Id_Inspeccion_Det"),"Id_Sitio",selection.siteId())));batchRepository.query("SELECT Id_Problema FROM problemas WHERE Id_Inspeccion=?",inspectionId).forEach(row->problems.add(Map.of("Id_Problema",row.get("Id_Problema"),"Id_Sitio",selection.siteId())));batchRepository.query("SELECT Id_Linea_Base FROM linea_base WHERE Id_Inspeccion=?",inspectionId).forEach(row->baselines.add(Map.of("Id_Linea_Base",row.get("Id_Linea_Base"),"Id_Sitio",selection.siteId())));}
		batchRepository.updateDerived("inspecciones_det","Id_Inspeccion_Det",details);batchRepository.updateDerived("problemas","Id_Problema",problems);batchRepository.updateDerived("linea_base","Id_Linea_Base",baselines);
	}

	private void phase(String id,String phase,int progress,LegacyEtlContext context,LegacyEtlReportBuilder report,Runnable action){
		jobs.updateState(id,LegacyImportStatus.PROCESSING,phase,progress,null,null);
		LegacyEtlReportBuilder.Checkpoint checkpoint=report.checkpoint();long started=System.nanoTime(),rowsBefore=report.sourceCount(),batchesBefore=context.batches();
		try{transactions.executeWithoutResult(status->{action.run();context.flushPending();report.assertBalanced();});context.finishPhase();}
		catch(RuntimeException exception){report.restore(checkpoint);throw exception;}
		long durationMs=(System.nanoTime()-started)/1_000_000L,memoryBytes=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
		log.info("LEGACY IMPORT - {} rows={} duration={}ms memoryUsed={}MB batches={}",phase,report.sourceCount()-rowsBefore,durationMs,memoryBytes/(1024*1024),context.batches()-batchesBefore);
	}
	public LegacyEtlReport result(String id,String createdBy){LegacyImportJob job=jobs.findById(id).orElseThrow(()->new ResourceNotFoundException("Importación legacy no encontrada"));if(createdBy==null||job.createdBy()==null||!job.createdBy().equalsIgnoreCase(createdBy))throw new ResourceNotFoundException("Importación legacy no encontrada");Path path=reportPath(id);if(!Files.isRegularFile(path))throw new ResourceNotFoundException("Conciliación no disponible");try{return mapper.readValue(path.toFile(),LegacyEtlReport.class);}catch(IOException exception){throw new BusinessValidationException("No fue posible leer la conciliación");}}
	public String markdownResult(String id,String createdBy){LegacyImportJob job=jobs.findById(id).orElseThrow(()->new ResourceNotFoundException("Importación legacy no encontrada"));if(createdBy==null||job.createdBy()==null||!job.createdBy().equalsIgnoreCase(createdBy))throw new ResourceNotFoundException("Importación legacy no encontrada");Path path=markdownPath(id);if(!Files.isRegularFile(path))throw new ResourceNotFoundException("Reporte Markdown no disponible");try{return Files.readString(path);}catch(IOException exception){throw new BusinessValidationException("No fue posible leer el reporte Markdown");}}
	private Path reportPath(String id){return baseDirectory.resolve(id+"-report.json").normalize();}
	private Path markdownPath(String id){return baseDirectory.resolve(id+"-report.md").normalize();}
	private void writeArtifacts(String id,LegacyEtlReport value,String status,String error,LegacyImportJob job,LocalDateTime finishedAt)throws IOException{mapper.writerWithDefaultPrettyPrinter().writeValue(reportPath(id).toFile(),value);Files.writeString(markdownPath(id),markdown(value,status,error,job,finishedAt)+homologationTrace(value));}
	private String homologationTrace(LegacyEtlReport value){List<String> entries=value.warnings().stream().filter(message->message.startsWith("Usuario ")).toList();if(entries.isEmpty())return "";StringBuilder text=new StringBuilder("\n## Cambios de prioridad en ubicaciones compartidas\n\n");entries.forEach(message->text.append("- ").append(md(message)).append('\n'));return text.toString();}
	private String markdown(LegacyEtlReport value,String status,String error,LegacyImportJob job,LocalDateTime finishedAt){long source=value.tables().stream().mapToLong(row->row.source()).sum(),inserted=value.tables().stream().mapToLong(row->row.inserted()).sum(),updated=value.tables().stream().mapToLong(row->row.updated()).sum(),skipped=value.tables().stream().mapToLong(row->row.skipped()).sum(),errors=value.tables().stream().mapToLong(row->row.errors()).sum();StringBuilder text=new StringBuilder("# Importación histórica ETIC\n\n## Estado\n\n").append("- Estado: **").append(status).append("**\n- Fecha inicio: ").append(date(job.startedAt())).append("\n- Fecha fin: ").append(date(finishedAt)).append("\n- Archivo origen: ").append(md(job.filename())).append("\n\n## Resumen\n\n").append("- Origen: ").append(source).append("\n- Insertados: ").append(inserted).append("\n- Actualizados: ").append(updated).append("\n- Omitidos: ").append(skipped).append("\n- Errores: ").append(errors).append("\n\n## Por tabla\n\n| Tabla | Origen | Insertados | Actualizados | Omitidos | Errores |\n|---|---:|---:|---:|---:|---:|\n");value.tables().forEach(row->text.append('|').append(tableName(row.table())).append('|').append(row.source()).append('|').append(row.inserted()).append('|').append(row.updated()).append('|').append(row.skipped()).append('|').append(row.errors()).append("|\n"));text.append("\n## IDs omitidos\n\n| ID | Tabla | Motivo | Fecha origen | Fecha destino |\n|---|---|---|---|---|\n");value.outcomes().stream().filter(row->"SKIPPED".equals(row.action())).forEach(row->text.append('|').append(md(row.id())).append('|').append(tableName(row.table())).append('|').append(md(row.reason())).append('|').append(date(row.sourceDate())).append('|').append(date(row.destinationDate())).append("|\n"));text.append("\n## Errores\n\n| Tabla | ID | Motivo |\n|---|---|---|\n");if(error!=null)text.append("|General||").append(md(error)).append("|\n");value.tables().stream().filter(row->row.errors()>0).forEach(row->text.append('|').append(tableName(row.table())).append("||").append(row.errors()).append(" error(es)|\n"));if(error==null&&errors==0)text.append("|||Sin errores|\n");return text.toString();}
	private String tableName(String table){return switch(table){case "clientes"->"Clientes";case "sitios"->"Sitios";case "sitio_contactos"->"Contactos de sitios";case "equipos"->"Equipos";case "ubicaciones"->"Ubicaciones";case "inspecciones"->"Inspecciones";case "inspecciones_det"->"Detalles";case "linea_base"->"Línea base";case "problemas"->"Problemas";case "historial_problemas"->"Historial crónico";default->md(table);};}
	private String date(LocalDateTime value){return value==null?"":value.toString().replace('T',' ');}
	private String md(String value){return value==null?"":value.replace("|","\\|").replace("\r"," ").replace("\n"," ");}
	private String rootMessage(Throwable throwable){Throwable current=throwable;while(current.getCause()!=null)current=current.getCause();return current.getMessage()==null?"Error durante ETL legacy":current.getMessage();}
	private void deleteDirectory(Path directory){if(!directory.startsWith(baseDirectory)||!Files.exists(directory))return;try(var paths=Files.walk(directory)){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
