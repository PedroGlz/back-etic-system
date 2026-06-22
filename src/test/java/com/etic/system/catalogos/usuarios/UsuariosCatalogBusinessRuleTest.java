package com.etic.system.catalogos.usuarios;

import com.etic.system.catalogos.shared.application.rule.CatalogRuleContext;
import com.etic.system.catalogos.shared.domain.model.CatalogRecord;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsuariosCatalogBusinessRuleTest {

	private final UsuariosCatalogBusinessRule rule = new UsuariosCatalogBusinessRule();

	@Test
	void shouldRejectClientGroup() {
		CatalogRuleContext context = new StubCatalogRuleContext("Clientes");

		assertThrows(
			BusinessValidationException.class,
			() -> rule.validate(Map.of("groupId", "group-1"), true, context)
		);
	}

	@Test
	void shouldAllowNonClientGroup() {
		CatalogRuleContext context = new StubCatalogRuleContext("Administradores");

		assertDoesNotThrow(() -> rule.validate(Map.of("groupId", "group-1"), true, context));
	}

	private record StubCatalogRuleContext(String groupName) implements CatalogRuleContext {

		@Override
		public boolean activeReferenceExists(String catalogKey, String id) {
			return true;
		}

		@Override
		public Optional<CatalogRecord> findById(String catalogKey, String id) {
			return Optional.of(new CatalogRecord(Map.of("name", groupName)));
		}
	}
}
