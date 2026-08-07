package com.etic.system.clientes.infrastructure.in.rest;

import com.etic.system.clientes.application.service.ClienteService;
import com.etic.system.clientes.domain.model.Cliente;
import com.etic.system.clientes.infrastructure.in.rest.request.ClienteRequest;
import com.etic.system.clientes.infrastructure.in.rest.request.ClienteStatusRequest;
import com.etic.system.clientes.infrastructure.in.rest.response.ClienteResponse;
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
@RequestMapping("/api/clientes")
public class ClienteController {

	private final ClienteService service;

	public ClienteController(ClienteService service) {
		this.service = service;
	}

	@GetMapping
	public List<ClienteResponse> findAll() {
		return service.findAll().stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public ClienteResponse findById(@PathVariable String id) {
		return toResponse(service.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ClienteResponse create(@Valid @RequestBody ClienteRequest request, HttpSession session) {
		return toResponse(service.create(request, userId(session)));
	}

	@PutMapping("/{id}")
	public ClienteResponse update(@PathVariable String id, @Valid @RequestBody ClienteRequest request, HttpSession session) {
		return toResponse(service.update(id, request, userId(session)));
	}

	@PatchMapping("/{id}/estatus")
	public ClienteResponse changeStatus(@PathVariable String id, @Valid @RequestBody ClienteStatusRequest request, HttpSession session) {
		return toResponse(service.changeStatus(id, request.status(), userId(session)));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable String id, HttpSession session) {
		service.deactivate(id, userId(session));
	}

	private ClienteResponse toResponse(Cliente cliente) {
		return new ClienteResponse(cliente.id(), cliente.businessName(), cliente.commercialName(), cliente.rfc(), cliente.status());
	}

	private String userId(HttpSession session) {
		Object userId = session.getAttribute("userId");
		return userId == null ? null : userId.toString();
	}
}
