package com.etic.system.clientes.domain.port.out;

import com.etic.system.clientes.domain.model.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClientePersistencePort {

	List<Cliente> findAll();

	Optional<Cliente> findById(String id);

	Cliente create(Cliente cliente, String userId);

	Cliente update(String id, Cliente cliente, String userId);

	void updateStatus(String id, String status, String userId);

	boolean existsByRfc(String rfc, String excludedId);
}
