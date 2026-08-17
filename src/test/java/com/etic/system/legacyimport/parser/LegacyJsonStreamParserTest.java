package com.etic.system.legacyimport.parser;

import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.ValidationSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyJsonStreamParserTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final LegacyJsonStreamParser parser = new LegacyJsonStreamParser(objectMapper);

	@Test
	void analyzesCompleteSmallFixtureWithoutBlockingErrors() throws Exception {
		try (InputStream fixture = getClass().getResourceAsStream("/fixtures/etic-legacy-v1-small.json")) {
			assertThat(fixture).isNotNull();
			LegacyImportAnalysis result = parser.analyze(fixture, 100);

			assertThat(result.clientes()).isEqualTo(1);
			assertThat(result.sitios()).isEqualTo(1);
			assertThat(result.inspecciones()).isEqualTo(2);
			assertThat(result.detalles()).isEqualTo(2);
			assertThat(result.ubicaciones()).isEqualTo(2);
			assertThat(result.lineasBase()).isEqualTo(2);
			assertThat(result.problemas()).isEqualTo(3);
			assertThat(result.problemasCronicos()).isEqualTo(2);
			assertThat(result.aparicionesProblemas()).isEqualTo(3);
			assertThat(result.fotosProblemas()).isEqualTo(1);
			assertThat(result.alerts()).noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
		}
	}

	@Test
	void acceptsVersionedContractAndCountsCoreDatasets() throws Exception {
		ObjectNode root = emptyContract();
		root.withArray("customers").add(objectMapper.createObjectNode().put("CustomerID", "CUSTOMER-1"));
		root.withArray("customerSites").add(objectMapper.createObjectNode()
			.put("CustomerSiteID", "SITE-1").put("CustomerID", "CUSTOMER-1"));
		root.withArray("locations").add(objectMapper.createObjectNode()
			.put("LocationID", "LOCATION-1").put("CustomerSiteID", "SITE-1"));
		root.withArray("inspections").add(objectMapper.createObjectNode()
			.put("InspectionID", "INSPECTION-1").put("CustomerID", "CUSTOMER-1")
			.put("CustomerSiteID", "SITE-1").put("TemperatureUnit", "F"));

		LegacyImportAnalysis result = analyze(root, 100);

		assertThat(result.clientes()).isEqualTo(1);
		assertThat(result.sitios()).isEqualTo(1);
		assertThat(result.ubicaciones()).isEqualTo(1);
		assertThat(result.inspecciones()).isEqualTo(1);
		assertThat(result.alerts()).noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
	}

	@Test
	void reportsDuplicatesMissingReferencesCyclesAndUnknownTemperatureUnits() throws Exception {
		ObjectNode root = emptyContract();
		ArrayNode customers = root.withArray("customers");
		customers.add(objectMapper.createObjectNode().put("CustomerID", "DUPLICATE"));
		customers.add(objectMapper.createObjectNode().put("CustomerID", "DUPLICATE"));
		root.withArray("locations").add(objectMapper.createObjectNode()
			.put("LocationID", "L1").put("CustomerSiteID", "MISSING").put("ParentID", "L2"));
		root.withArray("locations").add(objectMapper.createObjectNode()
			.put("LocationID", "L2").put("CustomerSiteID", "MISSING").put("ParentID", "L1"));
		root.withArray("problems").add(objectMapper.createObjectNode()
			.put("ProblemID", "P1").put("PriorProblemID", "P2"));
		root.withArray("problems").add(objectMapper.createObjectNode()
			.put("ProblemID", "P2").put("PriorProblemID", "P1"));
		root.withArray("inspections").add(objectMapper.createObjectNode()
			.put("InspectionID", "I1").put("TemperatureUnit", "K"));

		LegacyImportAnalysis result = analyze(root, 100);

		assertThat(result.alerts()).extracting(issue -> issue.code()).contains(
			"DUPLICATE_ID", "MISSING_REFERENCE", "LOCATION_CYCLE", "CHRONIC_CYCLE", "UNKNOWN_TEMPERATURE_UNIT"
		);
	}

	@Test
	void stopsWhenConfiguredRecordLimitIsExceeded() throws Exception {
		ObjectNode root = emptyContract();
		root.withArray("customers").add(objectMapper.createObjectNode().put("CustomerID", "C1"));
		root.withArray("customers").add(objectMapper.createObjectNode().put("CustomerID", "C2"));

		assertThatThrownBy(() -> analyze(root, 1))
			.isInstanceOf(LegacyJsonFormatException.class)
			.hasMessageContaining("límite configurable");
	}

	private LegacyImportAnalysis analyze(ObjectNode root, long maxRecords) throws Exception {
		byte[] json = objectMapper.writeValueAsBytes(root);
		return parser.analyze(new ByteArrayInputStream(json), maxRecords);
	}

	private ObjectNode emptyContract() {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("formatVersion", 1);
		root.set("metadata", objectMapper.createObjectNode());
		for (String dataset : LegacyJsonContract.REQUIRED_DATASETS) {
			root.set(dataset, objectMapper.createArrayNode());
		}
		return root;
	}
}
