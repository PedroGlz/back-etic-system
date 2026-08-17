package com.etic.system.legacyimport.parser;

import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

@Component
public class LegacyJsonStreamParser {

	private final ObjectMapper objectMapper;
	private final JsonFactory jsonFactory;

	public LegacyJsonStreamParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.jsonFactory = objectMapper.getFactory();
	}

	public LegacyImportAnalysis analyze(InputStream inputStream, long maxRecords) {
		LegacyImportAccumulator accumulator = new LegacyImportAccumulator(maxRecords);
		Set<String> seenDatasets = new HashSet<>();
		Integer formatVersion = null;
		boolean metadataPresent = false;
		try (JsonParser parser = jsonFactory.createParser(inputStream)) {
			if (parser.nextToken() != JsonToken.START_OBJECT) {
				throw new LegacyJsonFormatException("La raíz del archivo debe ser un objeto JSON");
			}
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				if (parser.currentToken() != JsonToken.FIELD_NAME) {
					throw new LegacyJsonFormatException("Estructura JSON raíz inválida");
				}
				String field = parser.currentName();
				JsonToken valueToken = parser.nextToken();
				if ("formatVersion".equals(field)) {
					if (!valueToken.isNumeric()) {
						throw new LegacyJsonFormatException("formatVersion debe ser numérico");
					}
					formatVersion = parser.getIntValue();
				} else if ("metadata".equals(field)) {
					if (valueToken != JsonToken.START_OBJECT) {
						throw new LegacyJsonFormatException("metadata debe ser un objeto");
					}
					metadataPresent = true;
					parser.skipChildren();
				} else if (LegacyJsonContract.REQUIRED_DATASETS.contains(field)) {
					seenDatasets.add(field);
					readDataset(parser, valueToken, field, accumulator);
				} else {
					parser.skipChildren();
				}
			}
			return accumulator.finish(seenDatasets, formatVersion, metadataPresent);
		} catch (LegacyJsonFormatException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new LegacyJsonFormatException("El archivo no contiene JSON válido", exception);
		}
	}

	private void readDataset(
		JsonParser parser,
		JsonToken valueToken,
		String dataset,
		LegacyImportAccumulator accumulator
	) throws IOException {
		if (valueToken != JsonToken.START_ARRAY) {
			throw new LegacyJsonFormatException(dataset + " debe ser un arreglo");
		}
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			if (parser.currentToken() != JsonToken.START_OBJECT) {
				throw new LegacyJsonFormatException("Cada elemento de " + dataset + " debe ser un objeto");
			}
			JsonNode row = objectMapper.readTree(parser);
			accumulator.accept(dataset, row);
		}
	}
}
