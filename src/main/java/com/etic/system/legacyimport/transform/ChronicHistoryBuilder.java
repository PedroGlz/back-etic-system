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
		validateGraph(priorByProblem);
		Map<String, List<Appearance>> byProblem = new HashMap<>();
		for (Appearance appearance : appearances) byProblem.computeIfAbsent(appearance.problemId(), ignored -> new ArrayList<>()).add(appearance);
		Map<String, String> roots = new HashMap<>();
		for (String problemId : byProblem.keySet()) roots.put(problemId, root(problemId, priorByProblem));
		Map<String, List<Appearance>> families = new LinkedHashMap<>();
		for (Appearance appearance : appearances) families.computeIfAbsent(roots.get(appearance.problemId()), ignored -> new ArrayList<>()).add(appearance);
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

	private void validateGraph(Map<String, String> graph) {
		for (Map.Entry<String, String> edge : graph.entrySet()) {
			if (edge.getValue() != null && !graph.containsKey(edge.getValue()))
				throw new IllegalArgumentException("PriorProblemID inexistente: " + edge.getValue());
			Set<String> path = new HashSet<>();
			String current = edge.getKey();
			while (current != null) {
				if (!path.add(current)) throw new IllegalArgumentException("Ciclo crónico detectado en " + current);
				current = graph.get(current);
			}
		}
	}

	private String root(String problemId, Map<String, String> graph) {
		String current = problemId;
		while (graph.get(current) != null) current = graph.get(current);
		return current;
	}

	private String deterministicId(String current) {
		return UUID.nameUUIDFromBytes(("ETIC:HISTORY:" + current).getBytes(StandardCharsets.UTF_8)).toString().toUpperCase();
	}

	public record Appearance(String problemId, String problemInspectionId, String pieProblemInspectionId,
		String inspectionId, String siteId, LocalDateTime inspectionDate, Integer inspectionNumber, LocalDateTime createdAt) {}
	public record HistoryRelation(String id, Appearance current, String previousPieId, String originalPieId) {}
	public record Result(long families, List<HistoryRelation> relations) {}
}
