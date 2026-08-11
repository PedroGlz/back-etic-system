package com.etic.system.catalogos.ubicaciones;

import com.etic.system.catalogos.shared.application.rule.CatalogBusinessRule;
import com.etic.system.catalogos.shared.application.rule.CatalogRuleContext;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UbicacionesCatalogBusinessRule implements CatalogBusinessRule {

	@Override
	public String catalogKey() {
		return "ubicaciones";
	}

	@Override
	public void validate(Map<String, Object> values, boolean creating, CatalogRuleContext context) {
		boolean equipment = "SI".equalsIgnoreCase(String.valueOf(values.get("isEquipment")));
		Object categoryId = values.get("equipmentCategoryId");
		if (equipment && (categoryId == null || categoryId.toString().isBlank())) {
			throw new BusinessValidationException("La categoría de equipo es obligatoria cuando la ubicación es un equipo");
		}
	}
}
