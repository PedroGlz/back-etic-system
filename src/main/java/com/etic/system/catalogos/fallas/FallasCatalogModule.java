package com.etic.system.catalogos.fallas;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class FallasCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "fallas";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"fallas", "Fallas", "fallas", "Id_Falla", "name", true,
			List.of(
				referenceField("inspectionTypeId", "Tipo de inspección", true, "tipos-inspeccion"),
				referenceField("failureTypeId", "Tipo de falla", true, "tipos-falla"),
				field("name", "Falla", true, 1000)
			),
			"id", "Id_Falla", "inspectionTypeId", "Id_Tipo_Inspeccion", "failureTypeId", "Id_Tipo_Falla",
			"name", "Falla", "status", "Estatus"
		).withWorkflowColumns();
	}
}
