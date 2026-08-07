package com.etic.system.sitios.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SitioRequest(
	@NotBlank String clientId,
	String siteGroupId,
	@NotBlank @Size(max = 300) String name,
	@Size(max = 1000) String description,
	@Size(max = 500) String address,
	@Size(max = 200) String neighborhood,
	@Size(max = 150) String state,
	@Size(max = 150) String municipality,
	@Size(max = 200) String contact1,
	@Size(max = 200) String contactRole1,
	@Size(max = 200) String contact2,
	@Size(max = 200) String contactRole2,
	@Size(max = 200) String contact3,
	@Size(max = 200) String contactRole3
) {
}
