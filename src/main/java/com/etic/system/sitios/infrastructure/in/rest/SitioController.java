package com.etic.system.sitios.infrastructure.in.rest;

import com.etic.system.sitios.application.service.SitioService;
import com.etic.system.sitios.domain.model.Sitio;
import com.etic.system.sitios.infrastructure.in.rest.request.SitioRequest;
import com.etic.system.sitios.infrastructure.in.rest.request.SitioStatusRequest;
import com.etic.system.sitios.infrastructure.in.rest.response.SitioResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sitios")
public class SitioController {

	private final SitioService service;

	public SitioController(SitioService service) {
		this.service = service;
	}

	@GetMapping
	public List<SitioResponse> findAll() {
		return service.findAll().stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public SitioResponse findById(@PathVariable String id) {
		return toResponse(service.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SitioResponse create(@Valid @RequestBody SitioRequest request, HttpSession session) {
		return toResponse(service.create(request, userId(session)));
	}

	@PutMapping("/{id}")
	public SitioResponse update(@PathVariable String id, @Valid @RequestBody SitioRequest request, HttpSession session) {
		return toResponse(service.update(id, request, userId(session)));
	}

	@PatchMapping("/{id}/estatus")
	public SitioResponse changeStatus(@PathVariable String id, @Valid @RequestBody SitioStatusRequest request, HttpSession session) {
		return toResponse(service.changeStatus(id, request.status(), userId(session)));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable String id, HttpSession session) {
		service.deactivate(id, userId(session));
	}

	private SitioResponse toResponse(Sitio site) {
		return new SitioResponse(
			site.id(), site.clientId(), site.clientName(), site.siteGroupId(), site.siteGroupName(),
			site.name(), site.description(), site.address(), site.neighborhood(), site.state(),
			site.municipality(), site.status(), site.contacts()
		);
	}

	private String userId(HttpSession session) {
		Object userId = session.getAttribute("userId");
		return userId == null ? null : userId.toString();
	}
}
