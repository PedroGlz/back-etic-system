package com.etic.system.catalogos.shared.application.rule;

import java.util.Map;

public interface CatalogBusinessRule {

	String catalogKey();

	void validate(Map<String, Object> values, boolean creating, CatalogRuleContext context);
}
