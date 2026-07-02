package com.etic.system.shared.infrastructure.jackson;

import com.etic.system.inspecciones.infrastructure.in.rest.InspectionController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NullableLocalDateTimeDeserializerTest {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void shouldDeserializeEmptyAndZeroDatesAsNull() throws Exception {
		assertNull(read("{\"date\":null}").date());
		assertNull(read("{\"date\":\"\"}").date());
		assertNull(read("{\"date\":\"   \"}").date());
		assertNull(read("{\"date\":\"0000-00-00\"}").date());
		assertNull(read("{\"date\":\"0000-00-00 00:00:00\"}").date());
	}

	@Test
	void shouldDeserializeValidDateTime() throws Exception {
		assertEquals(LocalDateTime.of(2026, 6, 28, 10, 15), read("{\"date\":\"2026-06-28T10:15:00\"}").date());
	}

	@Test
	void shouldDeserializeInspectionEnddateAlias() throws Exception {
		InspectionController.UpsertInspectionRequest request = objectMapper.readValue("""
			{
				"clientId": "client-1",
				"siteGroupId": "group-1",
				"siteId": "site-1",
				"enddate": "2026-06-28T10:15:00"
			}
			""", InspectionController.UpsertInspectionRequest.class);

		assertEquals(LocalDateTime.of(2026, 6, 28, 10, 15), request.endDate());
	}

	@Test
	void shouldDeserializeInspectionStatusEnddateAlias() throws Exception {
		InspectionController.UpdateInspectionStatusRequest request = objectMapper.readValue("""
			{
				"statusId": "73F27007-76B3-11D3-82BF-00104BC75DC2",
				"enddate": "2026-06-28T10:15:00"
			}
			""", InspectionController.UpdateInspectionStatusRequest.class);

		assertEquals(LocalDateTime.of(2026, 6, 28, 10, 15), request.endDate());
	}

	private SampleRequest read(String json) throws Exception {
		return objectMapper.readValue(json, SampleRequest.class);
	}

	private record SampleRequest(
		@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = NullableLocalDateTimeDeserializer.class)
		LocalDateTime date
	) {
	}
}
