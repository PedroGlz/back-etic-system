package com.etic.system.catalogos.shared.infrastructure.in.rest;

import com.etic.system.catalogos.shared.application.service.CatalogService;
import com.etic.system.catalogos.shared.domain.model.CatalogRecord;
import com.etic.system.catalogos.shared.domain.model.CatalogSchema;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	public List<CatalogSchema> schemas() {
		return catalogService.schemas();
	}

	@GetMapping("/{catalogKey}")
	public List<CatalogRecord> findAll(@PathVariable String catalogKey) {
		return catalogService.findAll(catalogKey);
	}

	@GetMapping("/{catalogKey}/{id}")
	public CatalogRecord findById(@PathVariable String catalogKey, @PathVariable String id) {
		return catalogService.findById(catalogKey, id);
	}

	@PostMapping("/{catalogKey}")
	@ResponseStatus(HttpStatus.CREATED)
	public CatalogRecord create(
		@PathVariable String catalogKey,
		@RequestBody Map<String, Object> values,
		HttpSession session
	) {
		return catalogService.create(catalogKey, values, userId(session));
	}

	@PutMapping("/{catalogKey}/{id}")
	public CatalogRecord update(
		@PathVariable String catalogKey,
		@PathVariable String id,
		@RequestBody Map<String, Object> values,
		HttpSession session
	) {
		return catalogService.update(catalogKey, id, values, userId(session));
	}

	@DeleteMapping("/{catalogKey}/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable String catalogKey, @PathVariable String id, HttpSession session) {
		catalogService.deactivate(catalogKey, id, userId(session));
	}

	private String userId(HttpSession session) {
		Object userId = session.getAttribute("userId");
		return userId == null ? null : userId.toString();
	}
}
