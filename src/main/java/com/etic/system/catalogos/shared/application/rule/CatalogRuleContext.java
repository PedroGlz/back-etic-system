package com.etic.system.catalogos.shared.application.rule;

import com.etic.system.catalogos.shared.domain.model.CatalogRecord;

import java.util.Optional;

public interface CatalogRuleContext {

	boolean activeReferenceExists(String catalogKey, String id);

	Optional<CatalogRecord> findById(String catalogKey, String id);
}
