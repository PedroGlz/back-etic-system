package com.etic.system.catalogos.shared.application.service;

import com.etic.system.catalogos.grupos.GruposCatalogModule;
import com.etic.system.shared.domain.exception.CatalogNotSupportedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogRegistryTest {

	@Test
	void shouldReturnDefinitionForRegisteredCatalog() {
		CatalogRegistry registry = new CatalogRegistry(List.of(new GruposCatalogModule()));

		assertEquals("grupos", registry.definition("grupos").schema().key());
	}

	@Test
	void shouldThrowWhenCatalogIsUnknown() {
		CatalogRegistry registry = new CatalogRegistry(List.of(new GruposCatalogModule()));

		assertThrows(CatalogNotSupportedException.class, () -> registry.definition("desconocido"));
	}
}
