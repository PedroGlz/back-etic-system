package com.etic.system.catalogos.categoriasequipos;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class CategoriasEquiposCatalogModule implements CatalogModule {
	@Override public String key() { return "categorias-equipos"; }

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"categorias-equipos", "Categorías de equipos", "categorias_equipos", "id_categoria_equipo", "name", true,
			List.of(field("name", "Categoría de equipo", true, 200)),
			"id", "id_categoria_equipo", "name", "nombre_categoria", "status", "Estatus"
		).withWorkflowColumns();
	}
}
