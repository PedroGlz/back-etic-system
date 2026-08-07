package com.etic.system.catalogos.equipos;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class EquiposCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "equipos";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"equipos", "Equipos", "equipos", "Id_Equipo", "name", true,
			List.of(
				field("name", "Equipo", true, 1000),
				field("description", "Descripción", false, 1000)
			),
			"id", "Id_Equipo", "name", "Equipo", "description", "Descr_equipo", "status", "Estatus"
		).withWorkflowColumns();
	}
}
