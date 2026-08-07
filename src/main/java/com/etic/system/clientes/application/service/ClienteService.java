package com.etic.system.clientes.application.service;

import com.etic.system.clientes.domain.model.Cliente;
import com.etic.system.clientes.domain.port.out.ClientePersistencePort;
import com.etic.system.clientes.infrastructure.in.rest.request.ClienteRequest;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

	private static final String ACTIVE = "Activo";
	private static final String INACTIVE = "Inactivo";

	private final ClientePersistencePort persistencePort;

	public ClienteService(ClientePersistencePort persistencePort) {
		this.persistencePort = persistencePort;
	}

	public List<Cliente> findAll() {
		return persistencePort.findAll();
	}

	public Cliente findById(String id) {
		return persistencePort.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
	}

	@Transactional
	public Cliente create(ClienteRequest request, String userId) {
		validate(request, null);
		return persistencePort.create(toCliente(null, request, ACTIVE), userId);
	}

	@Transactional
	public Cliente update(String id, ClienteRequest request, String userId) {
		findById(id);
		validate(request, id);
		return persistencePort.update(id, toCliente(id, request, null), userId);
	}

	@Transactional
	public Cliente changeStatus(String id, String status, String userId) {
		findById(id);
		validateStatus(status);
		persistencePort.updateStatus(id, status, userId);
		return findById(id);
	}

	@Transactional
	public void deactivate(String id, String userId) {
		findById(id);
		persistencePort.updateStatus(id, INACTIVE, userId);
	}

	private void validate(ClienteRequest request, String currentId) {
		if (persistencePort.existsByRfc(request.rfc(), currentId)) {
			throw new BusinessValidationException("Ya existe un cliente con ese RFC");
		}
	}

	private void validateStatus(String status) {
		if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
			throw new BusinessValidationException("Estatus de cliente no permitido");
		}
	}

	private Cliente toCliente(String id, ClienteRequest request, String status) {
		return new Cliente(id, request.businessName(), request.commercialName(), request.rfc(), status);
	}
}
