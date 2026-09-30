package com.etic.system.sitios.domain.model;

import java.util.List;

public record Sitio(
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
