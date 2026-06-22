package com.etic.system.catalogos.tiposambiente;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.numberField;

@Component
public class TiposAmbienteCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "tipos-ambiente";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"tipos-ambiente", "Tipos de ambiente", "tipo_ambientes", "Id_Tipo_Ambiente", "name", false,
			List.of(
				field("name", "Nombre", true, 150),
				field("description", "Descripción", false, 1000),
				numberField("adjustment", "Ajuste", true)
			),
			"id", "Id_Tipo_Ambiente", "name", "Nombre", "description", "Descripcion", "adjustment", "Adjust", "status", "Estatus"
		);
	}
}
