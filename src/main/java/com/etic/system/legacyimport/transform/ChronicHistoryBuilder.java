package com.etic.system.legacyimport.transform;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChronicHistoryBuilder {

	public Result build(Map<String, String> priorByProblem, List<Appearance> appearances) {
		Map<String, String> roots = resolveRoots(priorByProblem);
		Map<String, List<Appearance>> families = new LinkedHashMap<>();
		Set<String> appearanceIds = new HashSet<>();
		for (Appearance appearance : appearances) {
			if (appearance.pieProblemInspectionId() == null || appearance.pieProblemInspectionId().isBlank())
				throw new IllegalArgumentException("PIEProblemInspectionID obligatorio en historial");
			if (!appearanceIds.add(appearance.pieProblemInspectionId()))
				throw new IllegalArgumentException("PIEProblemInspectionID duplicado en historial: " + appearance.pieProblemInspectionId());
			String root = roots.get(appearance.problemId());
			if (root == null) throw new IllegalArgumentException("ProblemID inexistente en historial: " + appearance.problemId());
			families.computeIfAbsent(root, ignored -> new ArrayList<>()).add(appearance);
		}
		List<HistoryRelation> relations = new ArrayList<>();
		Comparator<Appearance> order = Comparator.comparing(Appearance::siteId, Comparator.nullsFirst(String::compareTo))
			.thenComparing(Appearance::inspectionDate, Comparator.nullsFirst(LocalDateTime::compareTo))
			.thenComparing(Appearance::inspectionNumber, Comparator.nullsFirst(Integer::compareTo))
			.thenComparing(Appearance::createdAt, Comparator.nullsFirst(LocalDateTime::compareTo))
			.thenComparing(Appearance::pieProblemInspectionId);
		for (List<Appearance> family : families.values()) {
			family.sort(order);
			if (family.isEmpty()) continue;
			String original = family.getFirst().pieProblemInspectionId();
			for (int index = 0; index < family.size(); index++) {
				Appearance current = family.get(index);
				String previous = index == 0 ? null : family.get(index - 1).pieProblemInspectionId();
				relations.add(new HistoryRelation(deterministicId(current.pieProblemInspectionId()), current, previous, original));
			}
		}
		return new Result(families.size(), relations);
	}

	private Map<String, String> resolveRoots(Map<String, String> graph) {
		Map<String, String> roots = new HashMap<>();
		for (Map.Entry<String, String> edge : graph.entrySet())
			if (edge.getValue() != null && !graph.containsKey(edge.getValue()))
				throw new IllegalArgumentException("PriorProblemID inexistente: " + edge.getValue());
		for (String start : graph.keySet()) {
			if (roots.containsKey(start)) continue;
			Map<String, Boolean> path = new LinkedHashMap<>();
			String current = start;
			while (current != null && !roots.containsKey(current)) {
				if (path.putIfAbsent(current, Boolean.TRUE) != null)
					throw new IllegalArgumentException("Ciclo crónico detectado en " + current);
				current = graph.get(current);
			}
			String root = current == null ? last(path) : roots.get(current);
			path.keySet().forEach(problem -> roots.put(problem, root));
		}
		return roots;
	}

	private String last(Map<String, Boolean> path) {
		String last = null;
		for (String problem : path.keySet()) last = problem;
		return last;
	}

	private String deterministicId(String current) {
		return UUID.nameUUIDFromBytes(("ETIC:HISTORY:" + current).getBytes(StandardCharsets.UTF_8)).toString().toUpperCase();
	}

	public record Appearance(String problemId, String problemInspectionId, String pieProblemInspectionId,
		String inspectionId, String siteId, LocalDateTime inspectionDate, Integer inspectionNumber, LocalDateTime createdAt) {}
	public record HistoryRelation(String id, Appearance current, String previousPieId, String originalPieId) {}
	public record Result(long families, List<HistoryRelation> relations) {}
}
