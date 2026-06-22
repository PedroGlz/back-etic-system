package com.etic.system.catalogos.shared.domain.port.out;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CatalogPersistencePort {

	List<CatalogRecord> findAll(CatalogDefinition definition);

	Optional<CatalogRecord> findById(CatalogDefinition definition, String id);

	CatalogRecord create(CatalogDefinition definition, Map<String, Object> values, String userId);

	CatalogRecord update(CatalogDefinition definition, String id, Map<String, Object> values, String userId);

	void deactivate(CatalogDefinition definition, String id, String userId);

	boolean activeReferenceExists(CatalogDefinition definition, String id);
}
