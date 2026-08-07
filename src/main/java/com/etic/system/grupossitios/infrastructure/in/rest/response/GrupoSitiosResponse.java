package com.etic.system.grupossitios.infrastructure.in.rest.response;

public record GrupoSitiosResponse(
	String id,
	String clientId,
	String clientName,
	String name,
	String status
) {
}
