package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.model.LegacyImportError;
import com.etic.system.legacyimport.model.LegacyInspectionPresence;
import com.etic.system.legacyimport.model.LegacyValidationIssue;
import com.etic.system.legacyimport.parser.LegacyJsonFormatException;
import com.etic.system.legacyimport.parser.LegacyDatasetStore;
import com.etic.system.legacyimport.repository.LegacyImportJobRepository;
import com.etic.system.legacyimport.repository.LegacyBatchUpsertRepository;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LegacyImportService {

	private static final Set<String> STRUCTURAL_ERRORS = Set.of(
		"MISSING_FORMAT_VERSION", "UNSUPPORTED_FORMAT_VERSION", "MISSING_METADATA", "MISSING_DATASET"
	);

	private final LegacyImportJobRepository repository;
	private final LegacyDatasetStore store;
	private final LegacyImportProperties properties;
	private final Path importsDirectory;
	private final LegacyImportCleanupService cleanupService;
	private final LegacyBatchUpsertRepository batchRepository;
	private final ConcurrentMap<String, LegacyImportAnalysis> analysisCache = new ConcurrentHashMap<>();

	public LegacyImportService(
		LegacyImportJobRepository repository,
		LegacyDatasetStore store,
		LegacyImportProperties properties,
		StorageProperties storageProperties,
		LegacyImportCleanupService cleanupService,
		LegacyBatchUpsertRepository batchRepository
	) {
		this.repository = repository;
		this.store = store;
		this.properties = properties;
		this.importsDirectory = storageProperties.basePath().resolve("legacy-imports").normalize();
		this.cleanupService = cleanupService;
		this.batchRepository = batchRepository;
	}

	public LegacyImportJob upload(MultipartFile file, String createdBy) {
		cleanupService.cleanExpired();
		validateUpload(file);
		String id = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
		String originalFilename = safeOriginalFilename(file.getOriginalFilename());
		Path target = importPath(id);
		Path staged = stagingPath(id);
		LegacyImportJob job = new LegacyImportJob(
			id, originalFilename, LegacyImportStatus.UPLOADED, "UPLOAD", 0,
			LocalDateTime.now(), null, null, createdBy
		);
		repository.create(job);

		try {
			Files.createDirectories(importsDirectory);
			try (InputStream input = file.getInputStream()) {
				Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
			}
			repository.updateState(id, LegacyImportStatus.VALIDATING, "STRUCTURE", 25, null, null);
			LegacyImportAnalysis analysis = enrichInspections(store.analyzeAndStage(target, staged, properties.getMaxRecords()),staged);
			analysisCache.put(id, analysis);
			repository.updateState(id, LegacyImportStatus.READY, "ANALYSIS", 100, null, null);
			return repository.findById(id).orElseThrow();
		} catch (IOException | LegacyJsonFormatException exception) {
			fail(id, "ANALYSIS", exception);
			deleteQuietly(target);
			deleteDirectory(staged);
			throw new LegacyImportOperationException(id,"ANALYSIS",exception.getMessage(),exception);
		} catch (RuntimeException exception) {
			fail(id, "ANALYSIS", exception);
			deleteQuietly(target);
			deleteDirectory(staged);
			throw new LegacyImportOperationException(id,"ANALYSIS",exception.getMessage(),exception);
		}
	}

	public LegacyImportAnalysis analysis(String id, String createdBy) {
		LegacyImportJob job = repository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Importación legacy no encontrada"));
		validateOwner(job, createdBy);
		if (job.status() == LegacyImportStatus.FAILED) {
			throw new BusinessValidationException("La importación falló: " + job.errorMessage());
		}
		Path staged = stagingPath(id);
		if (!store.isReady(staged)) {
			throw new ResourceNotFoundException("Staging de importación legacy no encontrado");
		}
		return analysisCache.computeIfAbsent(id, ignored -> enrichInspections(store.readAnalysis(staged),staged));
	}

	LegacyImportAnalysis analysisForExecution(String id) {
		Path staged = stagingPath(id);
		if (!store.isReady(staged)) {
			throw new ResourceNotFoundException("Staging de importación legacy no encontrado");
		}
		return analysisCache.computeIfAbsent(id, ignored -> store.readAnalysis(staged));
	}

	private LegacyImportAnalysis enrichInspections(LegacyImportAnalysis analysis,Path staged){
		Map<String,Map<String,Object>> customers=new java.util.LinkedHashMap<>(),sites=new java.util.LinkedHashMap<>();List<Map<String,Object>> inspections=new java.util.ArrayList<>();
		store.forEach(staged,"customers",row->{String key=text(row,"CustomerID");if(key!=null)customers.put(key,row);});
		store.forEach(staged,"customerSites",row->{String key=text(row,"CustomerSiteID");if(key!=null)sites.put(key,row);});
		store.forEach(staged,"inspections",inspections::add);
		List<String> ids=inspections.stream().map(row->text(row,"InspectionID")).filter(java.util.Objects::nonNull).distinct().toList();Set<String> existing=new java.util.LinkedHashSet<>();
		if(!ids.isEmpty()){String placeholders=String.join(",",ids.stream().map(ignored->"?").toList());batchRepository.query("SELECT Id_Inspeccion FROM inspecciones WHERE Id_Inspeccion IN ("+placeholders+")",ids.toArray()).forEach(row->{String value=text(row,"Id_Inspeccion");if(value!=null)existing.add(value.toLowerCase(Locale.ROOT));});}
		return analysis.withInspections(inspections.stream().map(row->{String inspectionId=text(row,"InspectionID"),customerId=text(row,"CustomerID"),siteId=text(row,"CustomerSiteID");return new LegacyInspectionPresence(inspectionId,longValue(row.get("InspectionNo")),text(customers.get(customerId),"Name"),text(sites.get(siteId),"SiteName"),inspectionId!=null&&existing.contains(inspectionId.toLowerCase(Locale.ROOT)));}).toList());
	}
	private String text(Map<String,Object> row,String key){if(row==null||row.get(key)==null||row.get(key).toString().isBlank())return null;return row.get(key).toString().trim();}
	private Long longValue(Object value){if(value==null||value.toString().isBlank())return null;return value instanceof Number number?number.longValue():Long.valueOf(value.toString());}

	Path stagingDirectory(String id, String createdBy) {
		LegacyImportJob job = status(id, createdBy);
		Path staged = stagingPath(job.id());
		if (!store.isReady(staged)) throw new ResourceNotFoundException("Staging de importación legacy no encontrado");
		return staged;
	}

	public LegacyImportJob status(String id, String createdBy) {
		LegacyImportJob job = repository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Importación legacy no encontrada"));
		validateOwner(job, createdBy);
		return job;
	}

	public Optional<LegacyImportJob> active(String createdBy) {
		Optional<LegacyImportJob> active = repository.findActiveByUser(createdBy)
			.filter(job -> job.status() == LegacyImportStatus.PROCESSING
				|| job.status() == LegacyImportStatus.VALIDATING_RESULT);
		if (active.isEmpty()) return Optional.empty();

		LegacyImportJob job = active.get();
		if (store.isReady(stagingPath(job.id()))) return Optional.of(job);

		if (job.status() == LegacyImportStatus.PROCESSING
			|| job.status() == LegacyImportStatus.VALIDATING_RESULT) {
			fail(job.id(), "RECOVERY", new IllegalStateException("Staging temporal perdido o incompleto"));
		}
		return Optional.empty();
	}

	private void validateOwner(LegacyImportJob job, String createdBy) {
		if (createdBy == null || job.createdBy() == null || !job.createdBy().equalsIgnoreCase(createdBy)) {
			throw new ResourceNotFoundException("Importación legacy no encontrada");
		}
	}

	private void validateUpload(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessValidationException("El archivo SQL legacy es obligatorio");
		}
		String filename = safeOriginalFilename(file.getOriginalFilename());
		if (!filename.toLowerCase(Locale.ROOT).endsWith(".sql")) {
			throw new BusinessValidationException("Solo se aceptan archivos .sql");
		}
		if (file.getSize() > properties.getMaxFileSize().toBytes()) {
			throw new BusinessValidationException("El archivo excede el tamaño máximo permitido");
		}
	}

	private String safeOriginalFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new BusinessValidationException("El archivo debe tener nombre");
		}
		String normalized = originalFilename.replace('\\', '/');
		String safe;
		try {
			safe = Path.of(normalized).getFileName().toString();
		} catch (InvalidPathException exception) {
			throw new BusinessValidationException("Nombre de archivo no válido");
		}
		if (!safe.equals(normalized) || safe.contains("..")) {
			throw new BusinessValidationException("Nombre de archivo no válido");
		}
		return safe;
	}

	private Path importPath(String id) {
		Path path = importsDirectory.resolve(id + ".sql").normalize();
		if (!path.startsWith(importsDirectory)) {
			throw new BusinessValidationException("Ruta de importación no válida");
		}
		return path;
	}

	private Path stagingPath(String id) {
		Path path = importsDirectory.resolve(id + "-datasets").normalize();
		if (!path.startsWith(importsDirectory)) throw new BusinessValidationException("Ruta de staging no válida");
		return path;
	}

	public LegacyImportError error(String id,String createdBy){
		LegacyImportJob job=status(id,createdBy);
		return repository.findError(job.id()).orElseThrow(()->new ResourceNotFoundException("Diagnóstico de error no disponible"));
	}

	private void fail(String id,String stage,Throwable error) {
		analysisCache.remove(id);
		String message=error.getMessage()==null?"Error durante la importación histórica":error.getMessage();
		repository.updateState(id, LegacyImportStatus.FAILED, "VALIDATION", 100,
			message, LocalDateTime.now());
		repository.saveError(id,stage,message,error);
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// El job conserva el error; una limpieza programada puede retirar temporales residuales.
		}
	}

	private void deleteDirectory(Path directory) {
		if (!directory.startsWith(importsDirectory) || !Files.exists(directory)) return;
		try (var paths = Files.walk(directory)) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> deleteQuietly(path));
		} catch (IOException ignored) {}
	}
}
