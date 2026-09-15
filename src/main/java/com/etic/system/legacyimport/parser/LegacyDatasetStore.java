package com.etic.system.legacyimport.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class LegacyDatasetStore {
	private static final String ANALYSIS_FILE = "analysis.json";

	private final ObjectMapper mapper;
	private final JsonFactory factory;
	private final LegacySqlScriptParser sqlParser;

	public LegacyDatasetStore(ObjectMapper mapper, LegacySqlScriptParser sqlParser) {
		this.mapper = mapper;
		this.factory = mapper.getFactory();
		this.sqlParser = sqlParser;
	}

	public void stage(Path source, Path directory) {
		if (source.getFileName().toString().toLowerCase().endsWith(".sql")) {
			sqlParser.stage(source, directory);
			return;
		}
		try {
			Files.createDirectories(directory);
			try (JsonParser parser = factory.createParser(source.toFile())) {
				if (parser.nextToken() != JsonToken.START_OBJECT) throw new LegacyJsonFormatException("Raíz JSON inválida");
				while (parser.nextToken() != JsonToken.END_OBJECT) {
					String field = parser.currentName();
					JsonToken value = parser.nextToken();
					if (LegacyJsonContract.REQUIRED_DATASETS.contains(field)) writeDataset(parser, value, datasetPath(directory, field));
					else parser.skipChildren();
				}
			}
		} catch (IOException exception) {
			throw new LegacyJsonFormatException("No fue posible preparar datasets legacy", exception);
		}
	}

	public LegacyImportAnalysis analyzeAndStage(Path source, Path directory, long maxRecords) {
		LegacyImportAnalysis analysis = sqlParser.analyzeAndStage(source, directory, maxRecords);
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(ANALYSIS_FILE).toFile(), analysis);
			return analysis;
		} catch (IOException exception) {
			throw new LegacyJsonFormatException("No fue posible guardar el análisis legacy", exception);
		}
	}

	public LegacyImportAnalysis readAnalysis(Path directory) {
		try {
			return mapper.readValue(directory.resolve(ANALYSIS_FILE).toFile(), LegacyImportAnalysis.class);
		} catch (IOException exception) {
			throw new LegacyJsonFormatException("No fue posible recuperar el análisis legacy", exception);
		}
	}

	public boolean isReady(Path directory) {
		if (!Files.isRegularFile(directory.resolve(ANALYSIS_FILE))) return false;
		return LegacyJsonContract.REQUIRED_DATASETS.stream().allMatch(dataset -> Files.isRegularFile(datasetPath(directory, dataset)));
	}

	public void forEach(Path directory, String dataset, Consumer<Map<String, Object>> consumer) {
		Path file = datasetPath(directory, dataset);
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				consumer.accept(mapper.readValue(line, new TypeReference<LinkedHashMap<String, Object>>() {}));
			}
		} catch (IOException exception) {
			throw new LegacyJsonFormatException("No fue posible leer " + dataset, exception);
		}
	}

	public Path datasetPath(Path directory, String dataset) {
		if (!LegacyJsonContract.REQUIRED_DATASETS.contains(dataset)) throw new IllegalArgumentException("Dataset no permitido");
		return directory.resolve(dataset + ".ndjson").normalize();
	}

	private void writeDataset(JsonParser parser, JsonToken token, Path target) throws IOException {
		if (token != JsonToken.START_ARRAY) throw new LegacyJsonFormatException("Dataset inválido");
		try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
			StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			 JsonGenerator generator = factory.createGenerator(writer)) {
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				generator.copyCurrentStructure(parser);
				generator.flush();
				writer.newLine();
			}
		}
	}
}
