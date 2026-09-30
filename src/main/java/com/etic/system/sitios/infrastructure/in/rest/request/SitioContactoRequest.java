package com.etic.system.sitios.infrastructure.in.rest.request;

import jakarta.validation.constraints.Size;

public record SitioContactoRequest(
	String id,
	@Size(max = 200) String name,
	@Size(max = 200) String role
) {
}
