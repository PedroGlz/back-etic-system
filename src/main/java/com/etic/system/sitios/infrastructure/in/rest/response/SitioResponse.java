package com.etic.system.sitios.infrastructure.in.rest.response;

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
	String contact1,
	String contactRole1,
	String contact2,
	String contactRole2,
	String contact3,
	String contactRole3,
	String status
) {
}
