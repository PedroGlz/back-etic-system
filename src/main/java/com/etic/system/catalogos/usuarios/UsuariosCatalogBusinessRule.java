package com.etic.system.catalogos.usuarios;

import com.etic.system.catalogos.shared.application.rule.CatalogBusinessRule;
import com.etic.system.catalogos.shared.application.rule.CatalogRuleContext;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UsuariosCatalogBusinessRule implements CatalogBusinessRule {

	@Override
	public String catalogKey() {
		return "usuarios";
	}

	@Override
	public void validate(Map<String, Object> values, boolean creating, CatalogRuleContext context) {
		Object groupId = values.get("groupId");
		if (groupId == null) {
			return;
		}

		boolean clientGroupSelected = context.findById("grupos", groupId.toString())
			.map(record -> record.values().get("name"))
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.map(String::trim)
			.map(String::toLowerCase)
			.map(name -> name.equals("cliente") || name.equals("clientes"))
			.orElse(false);

		if (clientGroupSelected) {
			throw new BusinessValidationException("El grupo Cliente no está disponible para usuarios en el nuevo sistema");
		}
	}
}
