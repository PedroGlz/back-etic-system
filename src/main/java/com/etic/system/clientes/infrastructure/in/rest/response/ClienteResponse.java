package com.etic.system.clientes.infrastructure.in.rest.response;

public record ClienteResponse(
	String id,
	String businessName,
	String commercialName,
	String rfc,
	String status
) {
}
