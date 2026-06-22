package com.etic.system.shared.domain.exception;

public class CatalogNotSupportedException extends RuntimeException {

	public CatalogNotSupportedException(String catalogKey) {
		super("Catálogo no soportado: " + catalogKey);
	}
}
