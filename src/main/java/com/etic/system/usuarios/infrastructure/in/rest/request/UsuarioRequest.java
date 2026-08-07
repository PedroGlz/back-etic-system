package com.etic.system.usuarios.infrastructure.in.rest.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
	@NotBlank
	String groupId,

	@NotBlank
	@Size(max = 50)
	String username,

	@NotBlank
	@Size(max = 100)
	String name,

	@Size(max = 100)
	String password,

	@NotBlank
	@Email
	@Size(max = 300)
	String email,

	@Size(max = 15)
	String phone,

	@Size(max = 50)
	String certificationLevel
) {
}
