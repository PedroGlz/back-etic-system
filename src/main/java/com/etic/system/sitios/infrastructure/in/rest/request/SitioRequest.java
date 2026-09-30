package com.etic.system.sitios.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;

public record SitioRequest(
	@NotBlank String clientId,
	String siteGroupId,
	@NotBlank @Size(max = 300) String name,
	@Size(max = 1000) String description,
	@Size(max = 500) String address,
	@Size(max = 200) String neighborhood,
	@Size(max = 150) String state,
	@Size(max = 150) String municipality,
	@Valid List<SitioContactoRequest> contacts
) {
}
