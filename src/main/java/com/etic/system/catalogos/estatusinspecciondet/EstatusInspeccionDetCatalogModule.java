package com.etic.system.catalogos.estatusinspecciondet;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;

@Component
public class EstatusInspeccionDetCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "estatus-inspeccion-det";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"estatus-inspeccion-det", "Estatus Inspección ubicaciones", "estatus_inspeccion_det", "Id_Status_Inspeccion_Det", "name", true,
			List.of(
				field("name", "Estatus", true, 10),
				field("description", "Descripción", false, 1000)
			),
			"id", "Id_Status_Inspeccion_Det", "name", "Estatus_Inspeccion_Det", "description", "Desc_Estatus_Det", "status", "Estatus"
		);
	}
}
