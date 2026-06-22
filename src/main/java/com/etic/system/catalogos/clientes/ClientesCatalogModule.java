package com.etic.system.catalogos.clientes;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class ClientesCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "clientes";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"clientes", "Clientes", "clientes", "Id_Cliente", "businessName", true,
			List.of(
				field("businessName", "Razón social", true, 300),
				field("commercialName", "Nombre comercial", true, 300),
				field("rfc", "RFC", true, 50)
			),
			"id", "Id_Cliente", "businessName", "Razon_Social", "commercialName", "Nombre_Comercial",
			"rfc", "RFC", "status", "Estatus"
		);
	}
}
