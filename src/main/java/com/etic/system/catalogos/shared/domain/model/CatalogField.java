package com.etic.system.catalogos.shared.domain.model;

public record CatalogField(
	String name,
	String label,
	String type,
	boolean required,
	Integer maxLength,
	String referenceCatalog,
	boolean writeOnly
) {
}
