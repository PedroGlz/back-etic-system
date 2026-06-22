package com.etic.system.catalogos.fabricantes;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class FabricantesCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "fabricantes";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"fabricantes", "Fabricantes", "fabricantes", "Id_Fabricante", "name", true,
			List.of(
				referenceField("inspectionTypeId", "Tipo de inspección", true, "tipos-inspeccion"),
				field("name", "Fabricante", true, 200),
				field("description", "Descripción", false, 1000)
			),
			"id", "Id_Fabricante", "inspectionTypeId", "Id_Tipo_Inspeccion", "name", "Fabricante",
			"description", "Desc_Fabricante", "status", "Estatus"
		);
	}
}
