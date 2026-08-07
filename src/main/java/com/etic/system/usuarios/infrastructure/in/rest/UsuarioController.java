package com.etic.system.usuarios.infrastructure.in.rest;

import com.etic.system.usuarios.application.service.UsuarioService;
import com.etic.system.usuarios.domain.model.GrupoUsuario;
import com.etic.system.usuarios.domain.model.Usuario;
import com.etic.system.usuarios.infrastructure.in.rest.request.UsuarioRequest;
import com.etic.system.usuarios.infrastructure.in.rest.request.UsuarioStatusRequest;
import com.etic.system.usuarios.infrastructure.in.rest.response.GrupoUsuarioResponse;
import com.etic.system.usuarios.infrastructure.in.rest.response.UsuarioResponse;
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
@RequestMapping("/api/usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@GetMapping
	public List<UsuarioResponse> findAll() {
		return usuarioService.findAll().stream().map(this::toResponse).toList();
	}

	@GetMapping("/grupos")
	public List<GrupoUsuarioResponse> groups() {
		return usuarioService.findActiveGroups().stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public UsuarioResponse findById(@PathVariable String id) {
		return toResponse(usuarioService.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse create(@Valid @RequestBody UsuarioRequest request, HttpSession session) {
		return toResponse(usuarioService.create(request, userId(session)));
	}

	@PutMapping("/{id}")
	public UsuarioResponse update(
		@PathVariable String id,
		@Valid @RequestBody UsuarioRequest request,
		HttpSession session
	) {
		return toResponse(usuarioService.update(id, request, userId(session)));
	}

	@PatchMapping("/{id}/estatus")
	public UsuarioResponse changeStatus(
		@PathVariable String id,
		@Valid @RequestBody UsuarioStatusRequest request,
		HttpSession session
	) {
		return toResponse(usuarioService.changeStatus(id, request.status(), userId(session)));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable String id, HttpSession session) {
		usuarioService.deactivate(id, userId(session));
	}

	private UsuarioResponse toResponse(Usuario usuario) {
		return new UsuarioResponse(
			usuario.id(),
			usuario.groupId(),
			usuario.groupName(),
			usuario.username(),
			usuario.name(),
			usuario.email(),
			usuario.phone(),
			usuario.certificationLevel(),
			usuario.status()
		);
	}

	private GrupoUsuarioResponse toResponse(GrupoUsuario group) {
		return new GrupoUsuarioResponse(group.id(), group.name(), group.status());
	}

	private String userId(HttpSession session) {
		Object userId = session.getAttribute("userId");
		return userId == null ? null : userId.toString();
	}
}
