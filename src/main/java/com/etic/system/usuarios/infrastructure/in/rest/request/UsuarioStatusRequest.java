package com.etic.system.usuarios.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;

public record UsuarioStatusRequest(
	@NotBlank
	String status
) {
}
