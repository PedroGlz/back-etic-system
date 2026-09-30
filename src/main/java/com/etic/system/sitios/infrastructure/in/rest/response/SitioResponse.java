package com.etic.system.sitios.infrastructure.in.rest.response;

import com.etic.system.sitios.domain.model.SitioContacto;
import java.util.List;

public record SitioResponse(
	String id,
	String clientId,
	String clientName,
	String siteGroupId,
	String siteGroupName,
	String name,
	String description,
	String address,
	String neighborhood,
	String state,
	String municipality,
	String status,
	List<SitioContacto> contacts
) {
}
