package com.etic.system.grupossitios.infrastructure.in.rest;

import com.etic.system.grupossitios.application.service.GrupoSitiosService;
import com.etic.system.grupossitios.domain.model.GrupoSitios;
import com.etic.system.grupossitios.infrastructure.in.rest.request.GrupoSitiosRequest;
import com.etic.system.grupossitios.infrastructure.in.rest.request.GrupoSitiosStatusRequest;
import com.etic.system.grupossitios.infrastructure.in.rest.response.GrupoSitiosResponse;
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
@RequestMapping("/api/grupos-sitios")
public class GrupoSitiosController {

	private final GrupoSitiosService service;

	public GrupoSitiosController(GrupoSitiosService service) {
		this.service = service;
	}

	@GetMapping
	public List<GrupoSitiosResponse> findAll() {
		return service.findAll().stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public GrupoSitiosResponse findById(@PathVariable String id) {
		return toResponse(service.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GrupoSitiosResponse create(@Valid @RequestBody GrupoSitiosRequest request, HttpSession session) {
		return toResponse(service.create(request, userId(session)));
	}

	@PutMapping("/{id}")
	public GrupoSitiosResponse update(@PathVariable String id, @Valid @RequestBody GrupoSitiosRequest request, HttpSession session) {
		return toResponse(service.update(id, request, userId(session)));
	}

	@PatchMapping("/{id}/estatus")
	public GrupoSitiosResponse changeStatus(@PathVariable String id, @Valid @RequestBody GrupoSitiosStatusRequest request, HttpSession session) {
		return toResponse(service.changeStatus(id, request.status(), userId(session)));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable String id, HttpSession session) {
		service.deactivate(id, userId(session));
	}

	private GrupoSitiosResponse toResponse(GrupoSitios item) {
		return new GrupoSitiosResponse(item.id(), item.clientId(), item.clientName(), item.name(), item.status());
	}

	private String userId(HttpSession session) {
		Object userId = session.getAttribute("userId");
		return userId == null ? null : userId.toString();
	}
}
