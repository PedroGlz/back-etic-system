package com.etic.system.catalogos.shared.domain.model;

import java.util.List;

public record CatalogSchema(String key, String title, List<CatalogField> fields) {
}
