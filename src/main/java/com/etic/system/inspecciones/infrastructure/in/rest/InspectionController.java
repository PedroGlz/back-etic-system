package com.etic.system.inspecciones.infrastructure.in.rest;

import com.etic.system.inspecciones.application.command.UpdateInspectionStatusCommand;
import com.etic.system.inspecciones.application.command.UpsertInspectionCommand;
import com.etic.system.inspecciones.application.service.InspectionPackageService;
import com.etic.system.inspecciones.application.service.InspectionService;
import com.etic.system.inspecciones.domain.model.InspectionSession;
import com.etic.system.inspecciones.domain.model.InspectionSummary;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/inspecciones")
public class InspectionController {

	private final InspectionService inspectionService;
	private final InspectionPackageService inspectionPackageService;

	public InspectionController(
		InspectionService inspectionService,
		InspectionPackageService inspectionPackageService
	) {
		this.inspectionService = inspectionService;
		this.inspectionPackageService = inspectionPackageService;
	}

	@GetMapping
	public List<InspectionSummary> findAll() {
		return inspectionService.findAll();
	}

	@GetMapping("/{inspectionId}")
	public InspectionSummary findById(@PathVariable String inspectionId) {
		return inspectionService.findById(inspectionId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public InspectionSummary create(@Valid @RequestBody UpsertInspectionRequest request, HttpSession session) {
		return inspectionService.create(request.toCommand(), userId(session));
	}

	@PutMapping("/{inspectionId}")
	public InspectionSummary update(
		@PathVariable String inspectionId,
		@Valid @RequestBody UpsertInspectionRequest request,
		HttpSession session
	) {
		return inspectionService.update(inspectionId, request.toCommand(), userId(session));
	}

	@PutMapping("/{inspectionId}/estatus")
	public InspectionSummary updateStatus(
		@PathVariable String inspectionId,
		@Valid @RequestBody UpdateInspectionStatusRequest request,
		HttpSession session
	) {
		return inspectionService.updateStatus(inspectionId, request.toCommand(), userId(session), session);
	}

	@PostMapping("/{inspectionId}/abrir")
	public InspectionSession open(@PathVariable String inspectionId, HttpSession session) {
		return inspectionService.open(inspectionId, session);
	}

	@GetMapping("/seleccion")
	public InspectionSession currentSelection(HttpSession session) {
		return inspectionService.currentSelection(session);
	}

	@DeleteMapping("/seleccion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clearSelection(HttpSession session) {
		inspectionService.clearSelection(session);
	}

	@DeleteMapping("/{inspectionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable String inspectionId, HttpSession session) {
		inspectionService.deactivate(inspectionId, userId(session), session);
	}

	@PostMapping("/{inspectionId}/exportar")
	public InspectionExportResponse export(
		@PathVariable String inspectionId,
		@RequestParam String fileName,
		@RequestParam String siteId
	) {
		InspectionPackageService.ExportedInspectionPackage exportedInspectionPackage =
			inspectionPackageService.exportInspection(inspectionId, siteId, fileName);
		return new InspectionExportResponse(
			200,
			exportedInspectionPackage.fileName(),
			"/api/inspecciones/exportaciones/" + exportedInspectionPackage.fileName()
		);
	}

	@GetMapping("/exportaciones/{fileName}")
	public ResponseEntity<Resource> downloadExport(@PathVariable String fileName) {
		Resource resource = inspectionPackageService.exportResource(fileName);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(resource.getFilename())
				.build()
				.toString())
			.body(resource);
	}

	@PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public InspectionImportResponse importResult(@RequestParam("bd_inspeccion") MultipartFile file) {
		InspectionPackageService.ImportInspectionResult result = inspectionPackageService.importInspectionResult(file);
		return new InspectionImportResponse(200, result.processedFiles(), result.paths());
	}

	@GetMapping("/{inspectionId}/reporte-problemas")
	public ResponseEntity<Resource> downloadProblemsReport(
		@PathVariable String inspectionId,
		@RequestParam String startDate,
		@RequestParam String endDate
	) {
		Resource resource = inspectionPackageService.generateProblemsReport(inspectionId, startDate, endDate);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(resource.getFilename())
				.build()
				.toString())
			.contentType(MediaType.parseMediaType("text/csv"))
			.body(resource);
	}

	private String userId(HttpSession session) {
		Object userId = session.getAttribute("userId");
		return userId == null ? null : userId.toString();
	}

	public record UpsertInspectionRequest(
		@NotBlank String clientId,
		@NotBlank String siteGroupId,
		@NotBlank String siteId,
		String statusId,
		String temperatureUnit,
		LocalDateTime startDate,
		LocalDateTime endDate
	) {
		UpsertInspectionCommand toCommand() {
			return new UpsertInspectionCommand(clientId, siteGroupId, siteId, statusId, temperatureUnit, startDate, endDate);
		}
	}

	public record UpdateInspectionStatusRequest(@NotBlank String statusId, LocalDateTime endDate) {
		UpdateInspectionStatusCommand toCommand() {
			return new UpdateInspectionStatusCommand(statusId, endDate);
		}
	}

	public record InspectionExportResponse(int status, String fileName, String downloadUrl) {
	}

	public record InspectionImportResponse(int status, int processedFiles, List<String> paths) {
	}
}
