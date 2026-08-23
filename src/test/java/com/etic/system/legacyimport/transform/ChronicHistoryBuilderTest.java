package com.etic.system.legacyimport.transform;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChronicHistoryBuilderTest {
	private final ChronicHistoryBuilder builder=new ChronicHistoryBuilder();
	@Test void buildsLinearHistoryAcrossChangedProblemIds(){Map<String,String> graph=new LinkedHashMap<>();graph.put("P1",null);graph.put("P2","P1");graph.put("P3","P2");var result=builder.build(graph,List.of(a("P3","PI3","PIE3",3),a("P1","PI1","PIE1",1),a("P2","PI2","PIE2",2)));assertThat(result.families()).isEqualTo(1);assertThat(result.relations()).extracting(ChronicHistoryBuilder.HistoryRelation::previousPieId).containsExactly(null,"PIE1","PIE2");assertThat(result.relations()).extracting(ChronicHistoryBuilder.HistoryRelation::originalPieId).containsOnly("PIE1");}
	@Test void generatesDeterministicIdsForIdempotence(){Map<String,String> graph=new LinkedHashMap<>();graph.put("P1",null);var first=builder.build(graph,List.of(a("P1","PI1","PIE1",1)));var second=builder.build(graph,List.of(a("P1","PI1","PIE1",1)));assertThat(first.relations().getFirst().id()).isEqualTo(second.relations().getFirst().id());}
	@Test void rejectsBrokenLinksAndCycles(){Map<String,String> broken=new LinkedHashMap<>();broken.put("P2","P1");assertThatThrownBy(()->builder.build(broken,List.of())).hasMessageContaining("inexistente");Map<String,String> cycle=Map.of("P1","P2","P2","P1");assertThatThrownBy(()->builder.build(cycle,List.of())).hasMessageContaining("Ciclo");}
	@Test void preservesEveryAppearanceInOneExactChain(){Map<String,String> graph=new LinkedHashMap<>();graph.put("P1",null);graph.put("P2","P1");var result=builder.build(graph,List.of(a("P2","PI3","PIE3",3),a("P1","PI1","PIE1",1),a("P1","PI2","PIE2",2)));assertThat(result.relations()).hasSize(3);assertThat(result.relations()).extracting(r->r.current().pieProblemInspectionId()).containsExactly("PIE1","PIE2","PIE3");assertThat(result.relations()).extracting(ChronicHistoryBuilder.HistoryRelation::previousPieId).containsExactly(null,"PIE1","PIE2");assertThat(result.relations()).extracting(ChronicHistoryBuilder.HistoryRelation::originalPieId).containsOnly("PIE1");}
	@Test void resolvesLongChainsWithoutQuadraticWalks(){Map<String,String> graph=new LinkedHashMap<>();for(int index=0;index<10_000;index++)graph.put("P"+index,index==0?null:"P"+(index-1));var result=builder.build(graph,List.of(a("P9999","PI","PIE",1)));assertThat(result.families()).isEqualTo(1);assertThat(result.relations()).hasSize(1);}
	@Test void rejectsDuplicateAppearanceIds(){Map<String,String> graph=new LinkedHashMap<>();graph.put("P1",null);assertThatThrownBy(()->builder.build(graph,List.of(a("P1","PI1","PIE",1),a("P1","PI2","PIE",2)))).hasMessageContaining("duplicado");}
	private ChronicHistoryBuilder.Appearance a(String problem,String pi,String pie,int number){return new ChronicHistoryBuilder.Appearance(problem,pi,pie,"I"+number,"S",LocalDateTime.of(2025,1,number,0,0),number,LocalDateTime.of(2025,1,number,1,0));}
}
