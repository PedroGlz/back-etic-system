package com.etic.system.catalogos.observacioneslineabase;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class ObservacionesLineaBaseCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "observaciones-linea-base";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"observaciones-linea-base", "Observaciones de línea base", "cat_observaciones_bl", "id_cat_observaciones_bl", "name", true,
			List.of(field("name", "Observación", true, 2000)),
			"id", "id_cat_observaciones_bl", "name", "observacion_bl", "status", "estatus"
		);
	}
}
