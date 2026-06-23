package com.etic.system.catalogos.tiposprioridad;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.booleanField;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class TiposPrioridadCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "tipos-prioridad";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"tipos-prioridad", "Tipos de prioridad", "tipo_prioridades", "Id_Tipo_Prioridad", "name", true,
			List.of(
				field("name", "Prioridad", true, 5),
				field("description", "Descripción", true, 1000),
				booleanField("isDefault", "Default", false)
			),
			"id", "Id_Tipo_Prioridad", "name", "Tipo_Prioridad", "description", "Desc_Prioridad",
			"isDefault", "is_default", "status", "Estatus"
		);
	}
}
