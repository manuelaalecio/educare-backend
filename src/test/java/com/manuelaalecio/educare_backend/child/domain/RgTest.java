package com.manuelaalecio.educare_backend.child.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RgTest {

	@ParameterizedTest
	@CsvSource({
			"12.345.678-9, 123456789",
			"12.345.678-x, 12345678X",
			"'12 345 678 X', 12345678X",
			"MG-12.345.678, MG12345678",
			"123456789, 123456789"})
	void shouldKeepOnlyLettersAndDigitsInUpperCaseWhenNormalizing(String rg, String expected) {
		assertThat(Rg.normalize(rg)).isEqualTo(expected);
	}

	@Test
	void shouldReturnEmptyWhenRgHasOnlyPunctuation() {
		assertThat(Rg.normalize("...-/ ")).isEmpty();
	}

	@Test
	void shouldReturnNullWhenNormalizingNull() {
		assertThat(Rg.normalize(null)).isNull();
	}

}
