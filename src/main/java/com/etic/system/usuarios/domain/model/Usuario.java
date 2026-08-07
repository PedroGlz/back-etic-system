package com.etic.system.usuarios.domain.model;

public record Usuario(
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
