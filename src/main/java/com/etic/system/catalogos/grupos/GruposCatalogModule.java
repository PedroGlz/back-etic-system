package com.etic.system.catalogos.grupos;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class GruposCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "grupos";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"grupos", "Grupos", "grupos", "Id_Grupo", "name", true,
			List.of(field("name", "Grupo", true, 150)),
			"id", "Id_Grupo", "name", "Grupo", "status", "Estatus"
		);
	}
}
