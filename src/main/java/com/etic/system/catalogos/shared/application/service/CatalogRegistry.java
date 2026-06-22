package com.etic.system.catalogos.shared.application.service;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import com.etic.system.catalogos.shared.domain.model.CatalogSchema;
import com.etic.system.shared.domain.exception.CatalogNotSupportedException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CatalogRegistry {

	private final Map<String, CatalogModule> modules;

	public CatalogRegistry(List<CatalogModule> catalogModules) {
		Map<String, CatalogModule> indexedModules = new LinkedHashMap<>();
		for (CatalogModule catalogModule : catalogModules) {
			indexedModules.put(catalogModule.key(), catalogModule);
		}
		this.modules = Map.copyOf(indexedModules);
	}

	public List<CatalogSchema> schemas() {
		return modules.values().stream().map(module -> module.definition().schema()).toList();
	}

	public CatalogDefinition definition(String catalogKey) {
		CatalogModule module = modules.get(catalogKey);
		if (module == null) {
			throw new CatalogNotSupportedException(catalogKey);
		}
		return module.definition();
	}
}
