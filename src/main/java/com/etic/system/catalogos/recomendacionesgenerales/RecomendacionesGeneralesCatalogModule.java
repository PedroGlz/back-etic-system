package com.etic.system.catalogos.recomendacionesgenerales;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class RecomendacionesGeneralesCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "recomendaciones-generales";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"recomendaciones-generales", "Recomendaciones generales", "recomendaciones_generales", "Id_Recomendacion_General", "name", true,
			List.of(field("name", "Recomendación general", true, 2000)),
			"id", "Id_Recomendacion_General", "name", "Recomendacion_General", "status", "Estatus"
		);
	}
}
