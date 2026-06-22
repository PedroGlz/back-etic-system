package com.etic.system.catalogos.sitios;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class SitiosCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "sitios";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"sitios", "Sitios", "sitios", "Id_Sitio", "name", true,
			List.of(
				referenceField("clientId", "Cliente", true, "clientes"),
				referenceField("siteGroupId", "Grupo de sitios", false, "grupos-sitios"),
				field("name", "Sitio", true, 300),
				field("description", "Descripción", false, 1000),
				field("address", "Dirección", false, 500),
				field("neighborhood", "Colonia", false, 200),
				field("state", "Estado", false, 150),
				field("municipality", "Municipio", false, 150),
				field("contact1", "Contacto 1", false, 200),
				field("contactRole1", "Puesto contacto 1", false, 200),
				field("contact2", "Contacto 2", false, 200),
				field("contactRole2", "Puesto contacto 2", false, 200),
				field("contact3", "Contacto 3", false, 200),
				field("contactRole3", "Puesto contacto 3", false, 200)
			),
			"id", "Id_Sitio", "clientId", "Id_Cliente", "siteGroupId", "Id_Grupo_Sitios", "name", "Sitio",
			"description", "Desc_Sitio", "address", "Direccion", "neighborhood", "Colonia", "state", "Estado",
			"municipality", "Municipio", "contact1", "Contacto_1", "contactRole1", "Puesto_Contacto_1",
			"contact2", "Contacto_2", "contactRole2", "Puesto_Contacto_2", "contact3", "Contacto_3",
			"contactRole3", "Puesto_Contacto_3", "status", "Estatus"
		);
	}
}
