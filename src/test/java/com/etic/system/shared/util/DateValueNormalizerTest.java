package com.etic.system.shared.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateValueNormalizerTest {

	@Test
	void shouldNormalizeEmptyDatabaseDateValuesToNull() {
		assertNull(DateValueNormalizer.normalizeDatabaseDateValue(null));
		assertNull(DateValueNormalizer.normalizeDatabaseDateValue(""));
		assertNull(DateValueNormalizer.normalizeDatabaseDateValue("   "));
	}

	@Test
	void shouldNormalizeZeroDatabaseDateValuesToNull() {
		assertNull(DateValueNormalizer.normalizeDatabaseDateValue("0000-00-00"));
		assertNull(DateValueNormalizer.normalizeDatabaseDateValue("0000-00-00 00:00:00"));
		assertNull(DateValueNormalizer.normalizeDatabaseDateValue("0000-00-00T00:00:00"));
	}

	@Test
	void shouldKeepValidDatabaseDateValue() {
		assertEquals("2026-06-28 10:15:00", DateValueNormalizer.normalizeDatabaseDateValue(" 2026-06-28 10:15:00 "));
	}

	@Test
	void shouldParseNullableLocalDateTime() {
		assertNull(DateValueNormalizer.parseNullableLocalDateTime(null));
		assertNull(DateValueNormalizer.parseNullableLocalDateTime(""));
		assertNull(DateValueNormalizer.parseNullableLocalDateTime("0000-00-00 00:00:00"));
		assertEquals(LocalDateTime.of(2026, 6, 28, 10, 15), DateValueNormalizer.parseNullableLocalDateTime("2026-06-28T10:15:00"));
		assertEquals(LocalDateTime.of(2026, 6, 28, 0, 0), DateValueNormalizer.parseNullableLocalDateTime("2026-06-28"));
	}
}
