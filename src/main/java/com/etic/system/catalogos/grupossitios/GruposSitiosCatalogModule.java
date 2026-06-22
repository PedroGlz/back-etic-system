package com.etic.system.catalogos.grupossitios;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class GruposSitiosCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "grupos-sitios";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"grupos-sitios", "Grupos de sitios", "grupos_sitios", "Id_Grupo_Sitios", "name", true,
			List.of(
				referenceField("clientId", "Cliente", true, "clientes"),
				field("name", "Grupo", true, 300)
			),
			"id", "Id_Grupo_Sitios", "clientId", "Id_Cliente", "name", "Grupo", "status", "Estatus"
		);
	}
}
