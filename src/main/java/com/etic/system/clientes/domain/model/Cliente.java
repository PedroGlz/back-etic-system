package com.etic.system.clientes.domain.model;

public record Cliente(
	String id,
	String businessName,
	String commercialName,
	String rfc,
	String status
) {
}
