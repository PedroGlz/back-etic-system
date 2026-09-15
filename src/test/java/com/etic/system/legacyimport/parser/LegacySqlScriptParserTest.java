package com.etic.system.legacyimport.parser;

import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.ValidationSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySqlScriptParserTest {
	private final ObjectMapper mapper = new ObjectMapper();
	private final LegacySqlScriptParser parser = new LegacySqlScriptParser(mapper);

	@Test
	void parsesSqlServerTokensMultilineAndIgnoresNonData(@TempDir Path temporary) throws Exception {
		String sql = """
			USE [Legacy]
			GO
			SET ANSI_NULLS ON
			-- comentario
			INSERT INTO [dbo].[Customers]
			([CustomerID],[Name],[Amount],[CreatedAt],[Optional],[ExternalID])
			VALUES ('C1',N'Juan''s Equipment',12.50,'2025-04-03 10:20:30',NULL,'550e8400-e29b-41d4-a716-446655440000');
			INSERT [dbo].[IgnoredTable] ([ID]) VALUES (1);
			""";
		Path source = temporary.resolve("fixture.sql");
		Files.writeString(source, sql, StandardCharsets.UTF_8);
		Path staged = temporary.resolve("staged");

		parser.stage(source, staged);

		JsonNode row = mapper.readTree(Files.readString(staged.resolve("customers.ndjson"), StandardCharsets.UTF_8));
		assertThat(row.path("Name").asText()).isEqualTo("Juan's Equipment");
		assertThat(row.path("Amount").decimalValue()).isEqualByComparingTo("12.50");
		assertThat(row.path("CreatedAt").asText()).isEqualTo("2025-04-03 10:20:30");
		assertThat(row.path("Optional").isNull()).isTrue();
		assertThat(row.path("ExternalID").asText()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
		assertThat(Files.readString(staged.resolve("customerSites.ndjson"))).isEmpty();
	}

	@Test
	void analyzesCompleteSqlFixtureWithoutBlockingErrors() throws Exception {
		try (InputStream fixture = getClass().getResourceAsStream("/fixtures/etic-legacy-v1-small.sql")) {
			assertThat(fixture).isNotNull();
			LegacyImportAnalysis result = parser.analyze(fixture, 100);
			assertThat(result.clientes()).isEqualTo(1);
			assertThat(result.sitios()).isEqualTo(1);
			assertThat(result.equipos()).isEqualTo(1);
			assertThat(result.inspecciones()).isEqualTo(2);
			assertThat(result.detalles()).isEqualTo(2);
			assertThat(result.ubicaciones()).isEqualTo(3);
			assertThat(result.lineasBase()).isEqualTo(1);
			assertThat(result.problemas()).isEqualTo(3);
			assertThat(result.problemasCronicos()).isEqualTo(2);
			assertThat(result.aparicionesProblemas()).isEqualTo(3);
			assertThat(result.aparicionesPie()).isEqualTo(3);
			assertThat(result.fotosProblemas()).isEqualTo(1);
			assertThat(result.fotosLineaBase()).isEqualTo(1);
			assertThat(result.relacionesEquipoFalla()).isEqualTo(1);
			assertThat(result.alerts()).noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
		}
	}

	@Test
	void analyzesAndStagesTheSameDatasetsInOnePass(@TempDir Path temporary) throws Exception {
		Path source=temporary.resolve("fixture.sql"),staged=temporary.resolve("staged");
		try(InputStream fixture=getClass().getResourceAsStream("/fixtures/etic-legacy-v1-small.sql")){
			Files.copy(fixture,source);
		}
		LegacyImportAnalysis result=parser.analyzeAndStage(source,staged,100);
		assertThat(LegacyJsonContract.REQUIRED_DATASETS).allSatisfy(dataset->assertThat(staged.resolve(dataset+".ndjson")).isRegularFile());
		assertThat(lineCount(staged.resolve("customers.ndjson"))).isEqualTo(result.clientes());
		assertThat(lineCount(staged.resolve("locations.ndjson"))).isEqualTo(result.ubicaciones());
		assertThat(lineCount(staged.resolve("locationBaselines.ndjson"))).isEqualTo(result.lineasBase());
		assertThat(lineCount(staged.resolve("problems.ndjson"))).isEqualTo(result.problemas());
		assertThat(lineCount(staged.resolve("pieProblemInspections.ndjson"))).isEqualTo(result.aparicionesPie());
	}

	private long lineCount(Path path) throws Exception { try(var lines=Files.lines(path)){return lines.count();} }

	@Test
	void parsesEmptyStringAndInsertWithoutInto() {
		String sql = "INSERT [dbo].[Customers] ([CustomerID],[Name]) VALUES ('C1','');";
		LegacyImportAnalysis result = parser.analyze(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)), 10);
		assertThat(result.clientes()).isEqualTo(1);
	}

	@Test
	void parsesConsecutiveSsmsInsertsWithoutSemicolon() {
		String sql = """
			SET IDENTITY_INSERT [dbo].[Customers] ON
			GO
			INSERT [dbo].[Customers] ([CustomerID],[Name]) VALUES ('C1',N'Uno')
			INSERT [dbo].[Customers] ([CustomerID],[Name]) VALUES ('C2',N'Dos')
			GO
			SET IDENTITY_INSERT [dbo].[Customers] OFF
			""";
		LegacyImportAnalysis result = parser.analyze(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)), 10);
		assertThat(result.clientes()).isEqualTo(2);
		assertThat(result.alerts()).noneMatch(issue -> issue.code().equals("DUPLICATE_ID"));
	}

	@Test
	void usesCreateTableMetadataForInsertWithoutColumnList() {
		String sql="""
			CREATE TABLE [dbo].[Customers] (
			 [CustomerID] [char](38) NOT NULL,
			 [Name] [nvarchar](50) NULL,
			 [DeleteFlag] [tinyint] NULL,
			 CONSTRAINT [PK_Customers] PRIMARY KEY ([CustomerID])
			);
			GO
			INSERT [dbo].[Customers] VALUES ('C1',N'Cliente esquema',0);
			GO
			""";
		LegacyImportAnalysis result=parser.analyze(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),10);
		assertThat(result.clientes()).isEqualTo(1);
		assertThat(result.alerts()).noneMatch(issue->issue.severity()==ValidationSeverity.ERROR);
	}

	@Test
	void parsesUtf16LeDataOnlyWithGoCastsAndMultilineStrings(@TempDir Path temporary) throws Exception {
		String sql = """
			INSERT [dbo].[Customers] ([CustomerID],[Name],[Optional],[CreatedAt]) VALUES
			(N'C1',N'Línea uno
			Línea ''dos''',NULL,CAST(N'2025-04-03 10:20:00' AS SmallDateTime))
			GO
			INSERT INTO dbo.Customers ([CustomerID],[Name],[Optional],[CreatedAt])
			VALUES (N'C2',N'Otro',NULL,CAST(N'2025-04-04 11:30:00' AS DateTime))
			GO
			""";
		Path source = temporary.resolve("utf16.sql");
		byte[] content = sql.getBytes(StandardCharsets.UTF_16LE);
		try (var output = Files.newOutputStream(source)) {
			output.write(0xFF); output.write(0xFE); output.write(content);
		}

		Path staged = temporary.resolve("staged");
		parser.stage(source, staged);
		var rows = Files.readAllLines(staged.resolve("customers.ndjson"), StandardCharsets.UTF_8);

		assertThat(rows).hasSize(2);
		JsonNode first = mapper.readTree(rows.getFirst());
		assertThat(first.path("Name").asText()).isEqualTo("Línea uno\nLínea 'dos'");
		assertThat(first.path("Optional").isNull()).isTrue();
		assertThat(first.path("CreatedAt").asText()).isEqualTo("2025-04-03 10:20:00");
	}
}
