package com.etic.system.legacyimport.transform;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LocationHierarchyBuilder {

	public Map<String, LocationPath> build(Map<String, LocationNode> nodes) {
		Map<String, LocationPath> result = new LinkedHashMap<>();
		for (String id : nodes.keySet()) resolve(id, nodes, result, new HashSet<>());
		return result;
	}

	private LocationPath resolve(String id, Map<String, LocationNode> nodes, Map<String, LocationPath> result, Set<String> visiting) {
		if (result.containsKey(id)) return result.get(id);
		LocationNode node = nodes.get(id);
		if (node == null) throw new IllegalArgumentException("Ubicación inexistente: " + id);
		if (!visiting.add(id)) throw new IllegalArgumentException("Ciclo de ubicaciones detectado en " + id);
		if (id.equals(node.parentId())) throw new IllegalArgumentException("Ubicación autorreferenciada: " + id);
		LocationPath parent = null;
		if (node.parentId() != null) {
			LocationNode parentNode = nodes.get(node.parentId());
			if (parentNode == null) throw new IllegalArgumentException("ParentID inexistente: " + node.parentId());
			if (!equals(node.siteId(), parentNode.siteId())) throw new IllegalArgumentException("ParentID pertenece a otro sitio: " + id);
			parent = resolve(node.parentId(), nodes, result, visiting);
		}
		String path = parent == null ? segment(node.name()) : parent.path() + "/" + segment(node.name());
		LocationPath value = new LocationPath(path, parent == null ? 0 : parent.level() + 1);
		result.put(id, value);
		visiting.remove(id);
		return value;
	}

	private boolean equals(String left, String right) { return left == null ? right == null : left.equals(right); }
	private String segment(String name) { return name == null || name.isBlank() ? "SIN_NOMBRE" : name.trim().replace("/", "-"); }

	public record LocationNode(String id, String siteId, String parentId, String name) {}
	public record LocationPath(String path, int level) {}
}
