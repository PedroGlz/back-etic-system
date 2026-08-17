package com.etic.system.legacyimport.transform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class TemperatureConverter {

	private TemperatureConverter() {}

	public static Double toCelsius(Object value, String sourceUnit) {
		if (value == null || value.toString().isBlank()) return null;
		double number = value instanceof Number numeric ? numeric.doubleValue() : Double.parseDouble(value.toString());
		if (sourceUnit == null) throw new UnknownTemperatureUnitException("Unidad de temperatura ausente");
		String unit=sourceUnit.trim().toUpperCase(Locale.ROOT).replace("°","").replace("º","").replace(" ","");
		double celsius=switch (unit) {
			case "C", "CELSIUS", "CENTIGRADE" -> number;
			case "F", "FAHRENHEIT" -> (number - 32D) / 1.8D;
			default -> throw new UnknownTemperatureUnitException("Unidad de temperatura desconocida: " + sourceUnit);
		};
		return BigDecimal.valueOf(celsius).setScale(0, RoundingMode.HALF_UP).doubleValue();
	}

	public static class UnknownTemperatureUnitException extends RuntimeException {
		public UnknownTemperatureUnitException(String message) { super(message); }
	}
}
