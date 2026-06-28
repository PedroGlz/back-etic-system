package com.etic.system.shared.infrastructure.jackson;

import com.etic.system.shared.util.DateValueNormalizer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;

public class NullableLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

	@Override
	public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		return DateValueNormalizer.parseNullableLocalDateTime(parser.getValueAsString());
	}
}
