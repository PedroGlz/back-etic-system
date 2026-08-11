package com.etic.system.catalogos.ubicaciones;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.booleanField;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class UbicacionesCatalogModule implements CatalogModule {
	@Override public String key() { return "ubicaciones"; }

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"ubicaciones", "Ubicaciones", "ubicaciones", "Id_Ubicacion", "name", true,
			List.of(
				referenceField("siteId", "Sitio", true, "sitios"),
				referenceField("parentLocationId", "Ubicación padre", false, "ubicaciones"),
				field("name", "Ubicación", true, 500),
				booleanField("isEquipment", "Es equipo", false),
				referenceField("equipmentCategoryId", "Categoría de equipo", false, "categorias-equipos")
			),
			"id", "Id_Ubicacion", "siteId", "Id_Sitio", "parentLocationId", "Id_Ubicacion_padre",
			"name", "Ubicacion", "isEquipment", "Es_Equipo", "equipmentCategoryId", "Id_Categoria_Equipo",
			"status", "Estatus"
		);
	}
}
