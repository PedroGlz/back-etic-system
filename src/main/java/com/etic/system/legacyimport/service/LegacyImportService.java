package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.model.LegacyValidationIssue;
import com.etic.system.legacyimport.parser.LegacyJsonFormatException;
import com.etic.system.legacyimport.parser.LegacySqlScriptParser;
import com.etic.system.legacyimport.repository.LegacyImportJobRepository;
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
import java.util.Set;
import java.util.UUID;

@Service
public class LegacyImportService {

	private static final Set<String> STRUCTURAL_ERRORS = Set.of(
		"MISSING_FORMAT_VERSION", "UNSUPPORTED_FORMAT_VERSION", "MISSING_METADATA", "MISSING_DATASET"
	);

	private final LegacyImportJobRepository repository;
	private final LegacySqlScriptParser parser;
	private final LegacyImportProperties properties;
	private final Path importsDirectory;
	private final LegacyImportCleanupService cleanupService;

	public LegacyImportService(
		LegacyImportJobRepository repository,
		LegacySqlScriptParser parser,
		LegacyImportProperties properties,
		StorageProperties storageProperties,
		LegacyImportCleanupService cleanupService
	) {
		this.repository = repository;
		this.parser = parser;
		this.properties = properties;
		this.importsDirectory = storageProperties.basePath().resolve("legacy-imports").normalize();
		this.cleanupService = cleanupService;
	}

	public LegacyImportJob upload(MultipartFile file, String createdBy) {
		cleanupService.cleanExpired();
		validateUpload(file);
		String id = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
		String originalFilename = safeOriginalFilename(file.getOriginalFilename());
		Path target = importPath(id);
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
			LegacyImportAnalysis analysis = analyze(target);
			repository.updateState(id, LegacyImportStatus.READY, "ANALYSIS", 100, null, null);
			return repository.findById(id).orElseThrow();
		} catch (IOException | LegacyJsonFormatException exception) {
			fail(id, exception.getMessage());
			deleteQuietly(target);
			throw new BusinessValidationException(exception.getMessage());
		} catch (RuntimeException exception) {
			fail(id, exception.getMessage());
			deleteQuietly(target);
			throw exception;
		}
	}

	public LegacyImportAnalysis analysis(String id, String createdBy) {
		LegacyImportJob job = repository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Importación legacy no encontrada"));
		validateOwner(job, createdBy);
		if (job.status() == LegacyImportStatus.FAILED) {
			throw new BusinessValidationException("La importación falló: " + job.errorMessage());
		}
		Path path = importPath(id);
		if (!Files.isRegularFile(path)) {
			throw new ResourceNotFoundException("Archivo de importación legacy no encontrado");
		}
		return analyze(path);
	}

	LegacyImportAnalysis analysisForExecution(String id) {
		Path path = importPath(id);
		if (!Files.isRegularFile(path)) {
			throw new ResourceNotFoundException("Archivo de importación legacy no encontrado");
		}
		return analyze(path);
	}

	public LegacyImportJob status(String id, String createdBy) {
		LegacyImportJob job = repository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Importación legacy no encontrada"));
		validateOwner(job, createdBy);
		return job;
	}

	private void validateOwner(LegacyImportJob job, String createdBy) {
		if (createdBy == null || job.createdBy() == null || !job.createdBy().equalsIgnoreCase(createdBy)) {
			throw new ResourceNotFoundException("Importación legacy no encontrada");
		}
	}

	private LegacyImportAnalysis analyze(Path path) {
		try (InputStream input = Files.newInputStream(path)) {
			return parser.analyze(input, properties.getMaxRecords());
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible leer el archivo legacy");
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

	private void fail(String id, String message) {
		repository.updateState(id, LegacyImportStatus.FAILED, "VALIDATION", 100,
			message == null ? "Error de validación" : message, LocalDateTime.now());
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// El job conserva el error; una limpieza programada puede retirar temporales residuales.
		}
	}
}
