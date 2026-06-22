package com.etic.system.catalogos.referenciasgenerales;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class ReferenciasGeneralesCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "referencias-generales";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"referencias-generales", "Referencias generales", "referencias_generales", "Id_Referencia_General", "name", true,
			List.of(field("name", "Referencia general", true, 2000)),
			"id", "Id_Referencia_General", "name", "Referencia_General", "status", "Estatus"
		);
	}
}
