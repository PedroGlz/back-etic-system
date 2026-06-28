package com.etic.system.shared.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DateValueNormalizer {

	private static final String ZERO_DATE = "0000-00-00";
	private static final String ZERO_DATETIME = "0000-00-00 00:00:00";
	private static final String ZERO_DATETIME_ISO = "0000-00-00T00:00:00";

	private DateValueNormalizer() {
	}

	public static Object normalizeDatabaseDateValue(Object value) {
		if (value instanceof String text) {
			String normalized = text.trim();
			return isBlankOrZeroDate(normalized) ? null : normalized;
		}
		return value;
	}

	public static LocalDateTime parseNullableLocalDateTime(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		if (isBlankOrZeroDate(normalized)) {
			return null;
		}
		if (normalized.length() == 10) {
			return LocalDate.parse(normalized).atStartOfDay();
		}
		return LocalDateTime.parse(normalized.replace(' ', 'T'));
	}

	private static boolean isBlankOrZeroDate(String value) {
		return value == null
			|| value.isBlank()
			|| ZERO_DATE.equals(value)
			|| ZERO_DATETIME.equals(value)
			|| ZERO_DATETIME_ISO.equals(value);
	}
}
