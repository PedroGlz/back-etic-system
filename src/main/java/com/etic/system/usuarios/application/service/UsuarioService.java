package com.etic.system.usuarios.application.service;

import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import com.etic.system.usuarios.domain.model.GrupoUsuario;
import com.etic.system.usuarios.domain.model.Usuario;
import com.etic.system.usuarios.domain.port.out.UsuarioPersistencePort;
import com.etic.system.usuarios.infrastructure.in.rest.request.UsuarioRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

	private static final String ACTIVE = "Activo";
	private static final String INACTIVE = "Inactivo";

	private final UsuarioPersistencePort persistencePort;

	public UsuarioService(UsuarioPersistencePort persistencePort) {
		this.persistencePort = persistencePort;
	}

	public List<Usuario> findAll() {
		return persistencePort.findAll();
	}

	public Usuario findById(String id) {
		return persistencePort.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
	}

	public List<GrupoUsuario> findActiveGroups() {
		return persistencePort.findActiveGroups().stream()
			.filter(group -> !isClientGroup(group.name()))
			.toList();
	}

	@Transactional
	public Usuario create(UsuarioRequest request, String userId) {
		if (request.password() == null || request.password().isBlank()) {
			throw new BusinessValidationException("La contraseña es obligatoria");
		}
		validate(request, null);
		return persistencePort.create(toUsuario(null, request, ACTIVE), request.password(), userId);
	}

	@Transactional
	public Usuario update(String id, UsuarioRequest request, String userId) {
		findById(id);
		validate(request, id);
		String password = request.password() == null || request.password().isBlank() ? null : request.password();
		return persistencePort.update(id, toUsuario(id, request, null), password, userId);
	}

	@Transactional
	public Usuario changeStatus(String id, String status, String userId) {
		findById(id);
		if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
			throw new BusinessValidationException("Estatus de usuario no permitido");
		}
		persistencePort.updateStatus(id, status, userId);
		return findById(id);
	}

	@Transactional
	public void deactivate(String id, String userId) {
		findById(id);
		persistencePort.updateStatus(id, INACTIVE, userId);
	}

	private void validate(UsuarioRequest request, String currentId) {
		GrupoUsuario group = persistencePort.findGroupById(request.groupId())
			.orElseThrow(() -> new BusinessValidationException("El grupo seleccionado no existe"));

		if (!ACTIVE.equals(group.status())) {
			throw new BusinessValidationException("El grupo seleccionado no está activo");
		}
		if (isClientGroup(group.name())) {
			throw new BusinessValidationException("El grupo Cliente no está disponible para usuarios en el nuevo sistema");
		}
		if (persistencePort.existsByUsername(request.username(), currentId)) {
			throw new BusinessValidationException("Ya existe un usuario con ese nombre de usuario");
		}
		if (persistencePort.existsByEmail(request.email(), currentId)) {
			throw new BusinessValidationException("Ya existe un usuario con ese correo electrónico");
		}
	}

	private Usuario toUsuario(String id, UsuarioRequest request, String status) {
		return new Usuario(
			id,
			request.groupId(),
			null,
			request.username(),
			request.name(),
			request.email(),
			blankToNull(request.phone()),
			blankToNull(request.certificationLevel()),
			status
		);
	}

	private boolean isClientGroup(String name) {
		String value = name == null ? "" : name.trim().toLowerCase();
		return value.equals("cliente") || value.equals("clientes");
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
