package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class LegacyEtlExecutionService {

	private final LegacyImportJobRepository jobs; private final LegacyImportService imports;
	private final LegacyDatasetStore store; private final LegacyBatchUpsertRepository batchRepository;
	private final LegacyImportProperties properties; private final TransactionTemplate transactions; private final ObjectMapper mapper;
	private final CatalogTransformService catalogs; private final CustomerTransformService customers; private final SiteTransformService sites;
	private final LocationTransformService locations; private final InspectionTransformService inspections; private final BaselineTransformService baselines;
	private final ProblemTransformService problems; private final ChronicHistoryTransformService chronicHistory; private final Path baseDirectory;
	private final LegacyResultValidationService finalValidation;

	public LegacyEtlExecutionService(LegacyImportJobRepository jobs,LegacyImportService imports,LegacyDatasetStore store,
		LegacyBatchUpsertRepository batchRepository,LegacyImportProperties properties,TransactionTemplate transactions,ObjectMapper mapper,
		CatalogTransformService catalogs,CustomerTransformService customers,SiteTransformService sites,LocationTransformService locations,
		InspectionTransformService inspections,BaselineTransformService baselines,ProblemTransformService problems,
		ChronicHistoryTransformService chronicHistory,LegacyResultValidationService finalValidation,StorageProperties storageProperties){
		this.jobs=jobs;this.imports=imports;this.store=store;this.batchRepository=batchRepository;this.properties=properties;this.transactions=transactions;this.mapper=mapper;
		this.catalogs=catalogs;this.customers=customers;this.sites=sites;this.locations=locations;this.inspections=inspections;this.baselines=baselines;this.problems=problems;this.chronicHistory=chronicHistory;
		this.finalValidation=finalValidation;
		this.baseDirectory=storageProperties.basePath().resolve("legacy-imports").normalize();
	}

	public LegacyEtlReport execute(String id,String createdBy){
		LegacyImportJob job=jobs.findById(id).orElseThrow(()->new ResourceNotFoundException("Importación legacy no encontrada"));
		if(job.status()!=LegacyImportStatus.READY&&job.status()!=LegacyImportStatus.COMPLETED)throw new BusinessValidationException("La importación no está lista para ejecutarse");
		if(createdBy==null||job.createdBy()==null||!job.createdBy().equalsIgnoreCase(createdBy))throw new ResourceNotFoundException("Importación legacy no encontrada");
		Path source=baseDirectory.resolve(id+".sql").normalize(), staged=baseDirectory.resolve(id+"-datasets").normalize();
		if(!source.startsWith(baseDirectory)||!Files.isRegularFile(source))throw new ResourceNotFoundException("Archivo legacy no encontrado");
		if(!jobs.claimForExecution(id,createdBy))throw new BusinessValidationException("La importación ya está en proceso o no está lista para ejecutarse");
		LegacyEtlReportBuilder report=new LegacyEtlReportBuilder(id);
		try{
			jobs.updateState(id,LegacyImportStatus.PROCESSING,"VALIDATION",2,null,null);
			LegacyImportAnalysis analysis=imports.analysisForExecution(id);
			if(analysis.alerts().stream().anyMatch(issue->issue.severity()==ValidationSeverity.ERROR))throw new BusinessValidationException("El análisis contiene errores estructurales o relaciones huérfanas");
			store.stage(source,staged);
			LegacyEtlContext context=new LegacyEtlContext(id,staged,store,batchRepository,report,properties.getBatchSize());
			phase(id,"CATALOGS",10,report,()->catalogs.transform(context));
			phase(id,"CUSTOMERS",20,report,()->customers.transform(context));
			phase(id,"SITES",30,report,()->sites.transform(context));
			phase(id,"LOCATIONS",42,report,()->locations.transform(context));
			phase(id,"INSPECTIONS",55,report,()->inspections.transform(context));
			phase(id,"BASELINES",68,report,()->baselines.transform(context));
			phase(id,"PROBLEMS",82,report,()->problems.transform(context));
			phase(id,"CHRONIC_HISTORY",93,report,()->chronicHistory.transform(context));
			jobs.updateState(id,LegacyImportStatus.VALIDATING_RESULT,"VALIDATION",97,null,null);
			finalValidation.validate(report);
			LegacyEtlReport result=report.build();writeArtifacts(id,result,"COMPLETED",null);
			context.shared().clear();jobs.updateState(id,LegacyImportStatus.COMPLETED,"VALIDATION",100,null,LocalDateTime.now());
			deleteDirectory(staged);return result;
		}catch(Exception exception){try{writeArtifacts(id,report.build(),"FAILED",rootMessage(exception));}catch(IOException ignored){}jobs.updateState(id,LegacyImportStatus.FAILED,"VALIDATION",100,rootMessage(exception),LocalDateTime.now());deleteDirectory(staged);
			if(exception instanceof BusinessValidationException validation)throw validation;throw new BusinessValidationException(rootMessage(exception));}
	}

	private void phase(String id,String phase,int progress,LegacyEtlReportBuilder report,Runnable action){jobs.updateState(id,LegacyImportStatus.PROCESSING,phase,progress,null,null);LegacyEtlReportBuilder.Checkpoint checkpoint=report.checkpoint();try{transactions.executeWithoutResult(status->{action.run();report.assertBalanced();});}catch(RuntimeException exception){report.restore(checkpoint);throw exception;}}
	public LegacyEtlReport result(String id,String createdBy){LegacyImportJob job=jobs.findById(id).orElseThrow(()->new ResourceNotFoundException("Importación legacy no encontrada"));if(createdBy==null||job.createdBy()==null||!job.createdBy().equalsIgnoreCase(createdBy))throw new ResourceNotFoundException("Importación legacy no encontrada");Path path=reportPath(id);if(!Files.isRegularFile(path))throw new ResourceNotFoundException("Conciliación no disponible");try{return mapper.readValue(path.toFile(),LegacyEtlReport.class);}catch(IOException exception){throw new BusinessValidationException("No fue posible leer la conciliación");}}
	public String markdownResult(String id,String createdBy){LegacyImportJob job=jobs.findById(id).orElseThrow(()->new ResourceNotFoundException("Importación legacy no encontrada"));if(createdBy==null||job.createdBy()==null||!job.createdBy().equalsIgnoreCase(createdBy))throw new ResourceNotFoundException("Importación legacy no encontrada");Path path=markdownPath(id);if(!Files.isRegularFile(path))throw new ResourceNotFoundException("Reporte Markdown no disponible");try{return Files.readString(path);}catch(IOException exception){throw new BusinessValidationException("No fue posible leer el reporte Markdown");}}
	private Path reportPath(String id){return baseDirectory.resolve(id+"-report.json").normalize();}
	private Path markdownPath(String id){return baseDirectory.resolve(id+"-report.md").normalize();}
	private void writeArtifacts(String id,LegacyEtlReport value,String status,String error)throws IOException{mapper.writerWithDefaultPrettyPrinter().writeValue(reportPath(id).toFile(),value);Files.writeString(markdownPath(id),markdown(value,status,error));}
	private String markdown(LegacyEtlReport value,String status,String error){StringBuilder text=new StringBuilder("# Reporte de importación legacy\n\n").append("- Importación: `").append(value.importId()).append("`\n- Estado: **").append(status).append("**\n");if(error!=null)text.append("- Error: ").append(md(error)).append("\n");text.append("\n## Conciliación por tabla\n\n| Tabla | Origen | Insertados | Actualizados | Omitidos | Errores | Huérfanos |\n|---|---:|---:|---:|---:|---:|---:|\n");value.tables().forEach(row->text.append('|').append(md(row.table())).append('|').append(row.source()).append('|').append(row.inserted()).append('|').append(row.updated()).append('|').append(row.skipped()).append('|').append(row.errors()).append('|').append(row.orphans()).append("|\n"));text.append("\n## Detalle por ID\n\n| Tabla | ID | Acción | Motivo | Fecha origen | Fecha MySQL |\n|---|---|---|---|---|---|\n");value.outcomes().forEach(row->text.append('|').append(md(row.table())).append('|').append(md(row.id())).append('|').append(md(row.action())).append('|').append(md(row.reason())).append('|').append(row.sourceDate()==null?"":row.sourceDate().toString().replace('T',' ')).append('|').append(row.destinationDate()==null?"":row.destinationDate().toString().replace('T',' ')).append("|\n"));if(!value.warnings().isEmpty()){text.append("\n## Advertencias\n\n");value.warnings().forEach(item->text.append("- ").append(md(item)).append('\n'));}return text.toString();}
	private String md(String value){return value==null?"":value.replace("|","\\|").replace("\r"," ").replace("\n"," ");}
	private String rootMessage(Throwable throwable){Throwable current=throwable;while(current.getCause()!=null)current=current.getCause();return current.getMessage()==null?"Error durante ETL legacy":current.getMessage();}
	private void deleteDirectory(Path directory){if(!directory.startsWith(baseDirectory)||!Files.exists(directory))return;try(var paths=Files.walk(directory)){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
