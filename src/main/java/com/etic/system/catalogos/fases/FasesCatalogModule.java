package com.etic.system.catalogos.fases;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class FasesCatalogModule implements CatalogModule {
	@Override public String key() { return "fases"; }

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"fases", "Fases", "fases", "Id_Fase", "name", true,
			List.of(
				field("name", "Fase", true, 200),
				field("description", "Descripción", false, 1000)
			),
			"id", "Id_Fase", "name", "Nombre_Fase", "description", "Descripcion", "status", "Estatus"
		).withWorkflowColumns();
	}
}
