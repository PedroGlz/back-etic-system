package com.etic.system.usuarios.domain.port.out;

import com.etic.system.usuarios.domain.model.GrupoUsuario;
import com.etic.system.usuarios.domain.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioPersistencePort {

	List<Usuario> findAll();

	Optional<Usuario> findById(String id);

	Usuario create(Usuario usuario, String password, String userId);

	Usuario update(String id, Usuario usuario, String password, String userId);

	void updateStatus(String id, String status, String userId);

	boolean existsByUsername(String username, String excludedId);

	boolean existsByEmail(String email, String excludedId);

	Optional<GrupoUsuario> findGroupById(String id);

	List<GrupoUsuario> findActiveGroups();
}
