package com.etic.system.catalogos.shared.application.service;

import com.etic.system.catalogos.shared.application.rule.CatalogBusinessRule;
import com.etic.system.catalogos.shared.application.rule.CatalogRuleContext;
import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogField;
import com.etic.system.catalogos.shared.domain.model.CatalogRecord;
import com.etic.system.catalogos.shared.domain.port.out.CatalogPersistencePort;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CatalogValidationService {

	private final CatalogRegistry catalogRegistry;
	private final CatalogPersistencePort catalogPersistencePort;
	private final Map<String, CatalogBusinessRule> rulesByCatalogKey;

	public CatalogValidationService(
		CatalogRegistry catalogRegistry,
		CatalogPersistencePort catalogPersistencePort,
		List<CatalogBusinessRule> rules
	) {
		this.catalogRegistry = catalogRegistry;
		this.catalogPersistencePort = catalogPersistencePort;
		Map<String, CatalogBusinessRule> indexedRules = new LinkedHashMap<>();
		for (CatalogBusinessRule rule : rules) {
			indexedRules.put(rule.catalogKey(), rule);
		}
		this.rulesByCatalogKey = Map.copyOf(indexedRules);
	}

	public Map<String, Object> validate(String catalogKey, CatalogDefinition definition, Map<String, Object> values, boolean creating) {
		Map<String, Object> validated = new LinkedHashMap<>();

		for (CatalogField field : definition.schema().fields()) {
			Object value = values.get(field.name());
			if (value instanceof String text) {
				value = text.trim();
			}

			boolean empty = value == null || value.toString().isBlank();
			if (field.required() && empty && (creating || !field.writeOnly())) {
				throw new BusinessValidationException(field.label() + " es obligatorio");
			}
			if (value != null && field.maxLength() != null && value.toString().length() > field.maxLength()) {
				throw new BusinessValidationException(field.label() + " no puede exceder " + field.maxLength() + " caracteres");
			}
			if (value != null && field.referenceCatalog() != null) {
				CatalogDefinition referenceDefinition = catalogRegistry.definition(field.referenceCatalog());
				if (!catalogPersistencePort.activeReferenceExists(referenceDefinition, value.toString())) {
					throw new BusinessValidationException(field.label() + " no es válido");
				}
			}

			validated.put(field.name(), value);
		}

		CatalogBusinessRule rule = rulesByCatalogKey.get(catalogKey);
		if (rule != null) {
			rule.validate(validated, creating, new DefaultCatalogRuleContext());
		}

		return validated;
	}

	private final class DefaultCatalogRuleContext implements CatalogRuleContext {

		@Override
		public boolean activeReferenceExists(String catalogKey, String id) {
			return catalogPersistencePort.activeReferenceExists(catalogRegistry.definition(catalogKey), id);
		}

		@Override
		public Optional<CatalogRecord> findById(String catalogKey, String id) {
			return catalogPersistencePort.findById(catalogRegistry.definition(catalogKey), id);
		}
	}
}
