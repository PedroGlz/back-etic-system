package com.etic.system.legacyimport.transform;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationHierarchyBuilderTest {
	private final LocationHierarchyBuilder builder=new LocationHierarchyBuilder();
	@Test void reconstructsPathAndLevel(){Map<String,LocationHierarchyBuilder.LocationNode> nodes=new LinkedHashMap<>();nodes.put("A",new LocationHierarchyBuilder.LocationNode("A","S",null,"Planta"));nodes.put("B",new LocationHierarchyBuilder.LocationNode("B","S","A","Tablero"));var result=builder.build(nodes);assertThat(result.get("B").path()).isEqualTo("Planta/Tablero");assertThat(result.get("B").level()).isEqualTo(1);}
	@Test void rejectsCycles(){Map<String,LocationHierarchyBuilder.LocationNode> nodes=Map.of("A",new LocationHierarchyBuilder.LocationNode("A","S","B","A"),"B",new LocationHierarchyBuilder.LocationNode("B","S","A","B"));assertThatThrownBy(()->builder.build(nodes)).hasMessageContaining("Ciclo");}
	@Test void rejectsOrphanAndCrossSiteParents(){Map<String,LocationHierarchyBuilder.LocationNode> orphan=Map.of("A",new LocationHierarchyBuilder.LocationNode("A","S","X","A"));assertThatThrownBy(()->builder.build(orphan)).hasMessageContaining("inexistente");Map<String,LocationHierarchyBuilder.LocationNode> cross=Map.of("A",new LocationHierarchyBuilder.LocationNode("A","S1",null,"A"),"B",new LocationHierarchyBuilder.LocationNode("B","S2","A","B"));assertThatThrownBy(()->builder.build(cross)).hasMessageContaining("otro sitio");}
}
