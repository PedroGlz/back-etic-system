package com.etic.system.catalogos.recomendaciones;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class RecomendacionesCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "recomendaciones";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"recomendaciones", "Recomendaciones", "recomendaciones", "Id_Recomendacion", "name", true,
			List.of(
				referenceField("inspectionTypeId", "Tipo de inspección", true, "tipos-inspeccion"),
				referenceField("rootCauseId", "Causa principal", true, "causas-principales"),
				field("name", "Recomendación", true, 2000)
			),
			"id", "Id_Recomendacion", "inspectionTypeId", "Id_Tipo_Inspeccion", "rootCauseId", "Id_Causa_Raiz",
			"name", "Recomendacion", "status", "Estatus"
		);
	}
}
