package com.etic.system.catalogos.tiposfalla;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class TiposFallaCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "tipos-falla";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"tipos-falla", "Tipos de falla", "tipo_fallas", "Id_Tipo_Falla", "name", true,
			List.of(
				referenceField("inspectionTypeId", "Tipo de inspección", true, "tipos-inspeccion"),
				field("name", "Tipo de falla", true, 300),
				field("description", "Descripción", false, 1000)
			),
			"id", "Id_Tipo_Falla", "inspectionTypeId", "Id_Tipo_Inspeccion", "name", "Tipo_Falla",
			"description", "Desc_Tipo_Falla", "status", "Estatus"
		);
	}
}
