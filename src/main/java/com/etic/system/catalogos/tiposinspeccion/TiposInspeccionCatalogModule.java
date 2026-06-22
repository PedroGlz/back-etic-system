package com.etic.system.catalogos.tiposinspeccion;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class TiposInspeccionCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "tipos-inspeccion";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"tipos-inspeccion", "Tipos de inspección", "tipo_inspecciones", "Id_Tipo_Inspeccion", "name", true,
			List.of(
				field("name", "Tipo de inspección", true, 150),
				field("description", "Descripción", false, 1000)
			),
			"id", "Id_Tipo_Inspeccion", "name", "Tipo_Inspeccion", "description", "Desc_Inspeccion", "status", "Estatus"
		);
	}
}
