package com.etic.system.catalogos.sitios;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public final class SitiosCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "sitios";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"sitios", "Sitios", "sitios", "Id_Sitio", "name", true,
			List.of(field("name", "Sitio", true, 200)),
			"id", "Id_Sitio", "name", "Sitio", "status", "Estatus"
		);
	}
}
