package com.etic.system.catalogos.causasprincipales;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class CausasPrincipalesCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "causas-principales";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"causas-principales", "Causas principales", "causa_principal", "Id_Causa_Raiz", "name", true,
			List.of(
				referenceField("inspectionTypeId", "Tipo de inspección", true, "tipos-inspeccion"),
				referenceField("failureId", "Falla", false, "fallas"),
				field("name", "Causa principal", true, 2000)
			),
			"id", "Id_Causa_Raiz", "inspectionTypeId", "Id_Tipo_Inspeccion", "failureId", "Id_Falla",
			"name", "Causa_Raiz", "status", "Estatus"
		);
	}
}
