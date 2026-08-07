package com.etic.system.catalogos.shared.application.service;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogRecord;
import com.etic.system.catalogos.shared.domain.model.CatalogSchema;
import com.etic.system.catalogos.shared.domain.port.out.CatalogPersistencePort;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CatalogService {

	private final CatalogRegistry catalogRegistry;
	private final CatalogPersistencePort catalogPersistencePort;
	private final CatalogValidationService catalogValidationService;

	public CatalogService(
		CatalogRegistry catalogRegistry,
		CatalogPersistencePort catalogPersistencePort,
		CatalogValidationService catalogValidationService
	) {
		this.catalogRegistry = catalogRegistry;
		this.catalogPersistencePort = catalogPersistencePort;
		this.catalogValidationService = catalogValidationService;
	}

	public List<CatalogSchema> schemas() {
		return catalogRegistry.schemas();
	}

	public List<CatalogRecord> findAll(String catalogKey) {
		CatalogDefinition definition = catalogRegistry.definition(catalogKey);
		List<CatalogRecord> records = catalogPersistencePort.findAll(definition);

		for (var field : definition.schema().fields()) {
			if (!"reference".equals(field.type()) || field.referenceCatalog() == null) {
				continue;
			}

			CatalogDefinition referenceDefinition = catalogRegistry.definition(field.referenceCatalog());
			Map<Object, String> labels = new LinkedHashMap<>();
			for (CatalogRecord reference : catalogPersistencePort.findAll(referenceDefinition)) {
				labels.put(reference.values().get("id"), displayValue(reference));
			}

			records = records.stream().map(record -> {
				Map<String, Object> values = new LinkedHashMap<>(record.values());
				values.put(field.name() + "Label", labels.get(record.values().get(field.name())));
				return new CatalogRecord(values);
			}).toList();
		}

		return records;
	}

	private String displayValue(CatalogRecord record) {
		for (String key : List.of("name", "businessName", "commercialName", "username", "email")) {
			Object value = record.values().get(key);
			if (value instanceof String text && !text.isBlank()) {
				return text;
			}
		}
		return String.valueOf(record.values().getOrDefault("id", ""));
	}

	public CatalogRecord findById(String catalogKey, String id) {
		CatalogDefinition definition = catalogRegistry.definition(catalogKey);
		return catalogPersistencePort.findById(definition, id)
			.orElseThrow(() -> new ResourceNotFoundException("Registro no encontrado"));
	}

	public CatalogRecord create(String catalogKey, Map<String, Object> values, String userId) {
		CatalogDefinition definition = catalogRegistry.definition(catalogKey);
		Map<String, Object> validatedValues = catalogValidationService.validate(catalogKey, definition, values, true);
		return catalogPersistencePort.create(definition, validatedValues, userId);
	}

	public CatalogRecord update(String catalogKey, String id, Map<String, Object> values, String userId) {
		CatalogDefinition definition = catalogRegistry.definition(catalogKey);
		findById(catalogKey, id);
		Map<String, Object> validatedValues = catalogValidationService.validate(catalogKey, definition, values, false);
		return catalogPersistencePort.update(definition, id, validatedValues, userId);
	}

	public void deactivate(String catalogKey, String id, String userId) {
		CatalogDefinition definition = catalogRegistry.definition(catalogKey);
		findById(catalogKey, id);
		catalogPersistencePort.deactivate(definition, id, userId);
	}
}
