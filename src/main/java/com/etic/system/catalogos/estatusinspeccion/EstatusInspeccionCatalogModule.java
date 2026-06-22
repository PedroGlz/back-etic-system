package com.etic.system.catalogos.estatusinspeccion;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class EstatusInspeccionCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "estatus-inspeccion";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"estatus-inspeccion", "Estatus de inspección", "estatus_inspeccion", "Id_Status_Inspeccion", "name", true,
			List.of(
				field("name", "Estatus", true, 30),
				field("description", "Descripción", false, 1000)
			),
			"id", "Id_Status_Inspeccion", "name", "Status_Inspeccion", "description", "Desc_Status", "status", "Estatus"
		);
	}
}
