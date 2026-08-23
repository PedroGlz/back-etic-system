package com.etic.system.shared.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateValueNormalizer {

	private static final String ZERO_DATE = "0000-00-00";
	private static final String ZERO_DATETIME = "0000-00-00 00:00:00";
	private static final String ZERO_DATETIME_ISO = "0000-00-00T00:00:00";
	private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");

	private DateValueNormalizer() {
	}

	public static Object normalizeDatabaseDateValue(Object value) {
		if (value instanceof String text) {
			String normalized = text.trim();
			if (isBlankOrZeroDate(normalized)) {
				return null;
			}
			if (normalized.matches("\\d{2}/\\d{2}/\\d{4}")) {
				return LocalDate.parse(normalized, LEGACY_DATE_FORMAT).toString();
			}
			return normalized;
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
