package com.etic.system.reportes.plantillas.application;

import com.etic.system.reportes.plantillas.domain.model.ReportTemplateFile;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ReportTemplateService {

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "xlsx", "xls");
	private static final String DOWNLOAD_BASE_URL = "/api/plantillas-reportes/descargar/";

	private final Path templatesDirectory = Path.of("storage", "plantillas-reportes");

	public List<ReportTemplateFile> list() {
		ensureDirectory();
		try (var stream = Files.list(templatesDirectory)) {
			return stream
				.filter(Files::isRegularFile)
				.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
				.map(this::toModel)
				.toList();
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible leer las plantillas de reportes");
		}
	}

	public void upload(List<MultipartFile> files) {
		ensureDirectory();
		if (files == null || files.isEmpty()) {
			throw new BusinessValidationException("Selecciona al menos un archivo para cargar");
		}

		for (MultipartFile file : files) {
			String fileName = sanitizeFileName(file.getOriginalFilename());
			String extension = extension(fileName);
			if (file.isEmpty()) {
				throw new BusinessValidationException("Uno de los archivos seleccionados está vacío");
			}
			if (!ALLOWED_EXTENSIONS.contains(extension)) {
				throw new BusinessValidationException("Solo se permiten archivos PDF y Excel para las plantillas");
			}

			Path target = templatesDirectory.resolve(fileName).normalize();
			validateInsideTemplates(target);
			try (InputStream inputStream = file.getInputStream()) {
				Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException exception) {
				throw new BusinessValidationException("No fue posible guardar la plantilla " + fileName);
			}
		}
	}

	public void delete(List<String> fileNames) {
		ensureDirectory();
		if (fileNames == null || fileNames.isEmpty()) {
			throw new BusinessValidationException("Selecciona al menos una plantilla para eliminar");
		}

		for (String rawName : fileNames) {
			String fileName = sanitizeFileName(rawName);
			Path target = templatesDirectory.resolve(fileName).normalize();
			validateInsideTemplates(target);
			try {
				Files.deleteIfExists(target);
			} catch (IOException exception) {
				throw new BusinessValidationException("No fue posible eliminar la plantilla " + fileName);
			}
		}
	}

	public Resource resource(String fileName) {
		ensureDirectory();
		String safeName = sanitizeFileName(fileName);
		Path target = templatesDirectory.resolve(safeName).normalize();
		validateInsideTemplates(target);
		if (!Files.exists(target) || !Files.isRegularFile(target)) {
			throw new ResourceNotFoundException("No se encontró la plantilla solicitada");
		}
		return new FileSystemResource(target);
	}

	public List<Path> filesForExport() {
		ensureDirectory();
		try (var stream = Files.list(templatesDirectory)) {
			return stream.filter(Files::isRegularFile).sorted().toList();
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible leer las plantillas de reportes");
		}
	}

	private ReportTemplateFile toModel(Path path) {
		try {
			String name = path.getFileName().toString();
			return new ReportTemplateFile(
				name,
				extension(name),
				Files.size(path),
				true,
				DOWNLOAD_BASE_URL + name
			);
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible leer la metadata de una plantilla");
		}
	}

	private void ensureDirectory() {
		try {
			Files.createDirectories(templatesDirectory);
		} catch (IOException exception) {
			throw new BusinessValidationException("No fue posible preparar el directorio de plantillas");
		}
	}

	private void validateInsideTemplates(Path target) {
		if (!target.startsWith(templatesDirectory.normalize())) {
			throw new BusinessValidationException("La ruta de la plantilla no es válida");
		}
	}

	private String sanitizeFileName(String rawName) {
		if (rawName == null || rawName.isBlank()) {
			throw new BusinessValidationException("El nombre del archivo no es válido");
		}
		String fileName = Path.of(rawName).getFileName().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
		if (fileName.isBlank()) {
			throw new BusinessValidationException("El nombre del archivo no es válido");
		}
		return fileName;
	}

	private String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
