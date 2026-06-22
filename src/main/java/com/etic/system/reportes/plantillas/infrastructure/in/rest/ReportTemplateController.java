package com.etic.system.reportes.plantillas.infrastructure.in.rest;

import com.etic.system.reportes.plantillas.application.ReportTemplateService;
import com.etic.system.reportes.plantillas.domain.model.ReportTemplateFile;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/plantillas-reportes")
public class ReportTemplateController {

	private final ReportTemplateService reportTemplateService;

	public ReportTemplateController(ReportTemplateService reportTemplateService) {
		this.reportTemplateService = reportTemplateService;
	}

	@GetMapping
	public List<ReportTemplateFile> list() {
		return reportTemplateService.list();
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public void upload(@RequestParam("files") List<MultipartFile> files) {
		reportTemplateService.upload(files);
	}

	@DeleteMapping
	public void delete(@RequestParam("files") List<String> fileNames) {
		reportTemplateService.delete(fileNames);
	}

	@GetMapping("/descargar/{fileName}")
	public ResponseEntity<Resource> download(@PathVariable String fileName) {
		Resource resource = reportTemplateService.resource(fileName);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(resource.getFilename())
				.build()
				.toString())
			.body(resource);
	}
}
