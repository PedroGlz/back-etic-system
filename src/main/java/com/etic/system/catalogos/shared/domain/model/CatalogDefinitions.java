package com.etic.system.catalogos.shared.domain.model;

public final class CatalogDefinitions {

	private CatalogDefinitions() {
	}

	public static CatalogField field(String name, String label, boolean required, Integer maxLength) {
		return new CatalogField(name, label, "text", required, maxLength, null, false);
	}

	public static CatalogField referenceField(String name, String label, boolean required, String referenceCatalog) {
		return new CatalogField(name, label, "reference", required, 38, referenceCatalog, false);
	}

	public static CatalogField passwordField() {
		return new CatalogField("password", "Contraseña", "password", true, 100, null, true);
	}

	public static CatalogField numberField(String name, String label, boolean required) {
		return new CatalogField(name, label, "number", required, null, null, false);
	}

	public static CatalogField booleanField(String name, String label, boolean required) {
		return new CatalogField(name, label, "boolean", required, 1, null, false);
	}
}
