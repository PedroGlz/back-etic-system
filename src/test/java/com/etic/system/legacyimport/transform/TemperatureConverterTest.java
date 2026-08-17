package com.etic.system.legacyimport.transform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemperatureConverterTest {
	@Test void keepsCelsius(){assertThat(TemperatureConverter.toCelsius(25,"C")).isEqualTo(25);}
	@Test void convertsFahrenheitOnlyWhenDeclared(){assertThat(TemperatureConverter.toCelsius(212,"F")).isEqualTo(100);}
	@Test void convertsDegreeAliasAndRoundsLikeLegacyPhp(){assertThat(TemperatureConverter.toCelsius(100,"°F")).isEqualTo(38);}
	@Test void rejectsUnknownUnit(){assertThatThrownBy(()->TemperatureConverter.toCelsius(10,"K")).isInstanceOf(TemperatureConverter.UnknownTemperatureUnitException.class);}
}
