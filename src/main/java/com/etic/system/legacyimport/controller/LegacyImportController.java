package com.etic.system.legacyimport.controller;

import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.service.LegacyImportService;
import com.etic.system.legacyimport.service.LegacyEtlExecutionService;
import com.etic.system.legacyimport.report.LegacyEtlReport;
import com.etic.system.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/legacy-imports")
public class LegacyImportController {

	private final LegacyImportService service;
	private final LegacyEtlExecutionService executionService;

	public LegacyImportController(LegacyImportService service, LegacyEtlExecutionService executionService) {
		this.service = service;
		this.executionService = executionService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public UploadResponse upload(@RequestParam("file") MultipartFile file, HttpSession session) {
		AuthenticatedUser user = requireAdministrator(session);
		LegacyImportJob job = service.upload(file, user.id());
		return new UploadResponse(job.id(), job.status());
	}

	@GetMapping("/{id}/analysis")
	public LegacyImportAnalysis analysis(@PathVariable String id, HttpSession session) {
		return service.analysis(id, requireAdministrator(session).id());
	}

	@PostMapping("/{id}/execute")
	public LegacyEtlReport execute(@PathVariable String id, HttpSession session) {
		return executionService.execute(id, requireAdministrator(session).id());
	}

	@GetMapping("/{id}")
	public JobStatusResponse status(@PathVariable String id, HttpSession session) {
		LegacyImportJob job=service.status(id,requireAdministrator(session).id());
		return new JobStatusResponse(job.id(),job.status(),job.phase(),job.progress(),job.errorMessage());
	}

	@GetMapping("/{id}/result")
	public LegacyEtlReport result(@PathVariable String id,HttpSession session){
		return executionService.result(id,requireAdministrator(session).id());
	}

	@GetMapping(value="/{id}/report.md",produces="text/markdown;charset=UTF-8")
	public ResponseEntity<String> markdown(@PathVariable String id,HttpSession session){
		String report=executionService.markdownResult(id,requireAdministrator(session).id());
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=legacy-import-"+id+".md").body(report);
	}

	private AuthenticatedUser requireAdministrator(HttpSession session) {
		Object value=session.getAttribute("authenticatedUser");
		if(!(value instanceof AuthenticatedUser user))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Se requiere una sesión activa");
		if(user.groupName()==null||!user.groupName().equalsIgnoreCase("Administradores"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Se requieren permisos de administrador");
		return user;
	}

	public record UploadResponse(String id, LegacyImportStatus status) {
	}

	public record JobStatusResponse(String id,LegacyImportStatus status,String phase,int progress,String errorMessage) {}
}
