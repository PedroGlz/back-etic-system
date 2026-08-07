package com.etic.system.grupossitios.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrupoSitiosRequest(
	@NotBlank
	String clientId,

	@NotBlank
	@Size(max = 300)
	String name
) {
}
