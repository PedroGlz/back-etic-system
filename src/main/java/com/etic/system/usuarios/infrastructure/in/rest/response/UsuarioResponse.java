package com.etic.system.usuarios.infrastructure.in.rest.response;

public record UsuarioResponse(
	String id,
	String groupId,
	String groupName,
	String username,
	String name,
	String email,
	String phone,
	String certificationLevel,
	String status
) {
}
