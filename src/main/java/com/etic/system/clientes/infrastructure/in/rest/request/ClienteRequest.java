package com.etic.system.clientes.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
	@NotBlank
	@Size(max = 300)
	String businessName,

	@NotBlank
	@Size(max = 300)
	String commercialName,

	@NotBlank
	@Size(max = 50)
	String rfc
) {
}
