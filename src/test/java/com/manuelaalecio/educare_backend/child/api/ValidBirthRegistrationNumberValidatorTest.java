package com.manuelaalecio.educare_backend.child.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidBirthRegistrationNumberValidatorTest {

	private final ValidBirthRegistrationNumberValidator validator = new ValidBirthRegistrationNumberValidator();

	@Test
	void shouldAcceptNumberWhenNull() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"10453901552013100012021000012321", "104539.01.55.2013.1.00012.021.0000123-21",
			"104539 01 55 2013 1 00012 021 0000123 21"})
	void shouldAcceptNumberWhenItHasThirtyTwoDigits(String number) {
		assertThat(validator.isValid(number, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"1045390155201310001202100001232", "104539015520131000120210000123210",
			"1045390155201310001202100001232X"})
	void shouldRejectNumberWhenItDoesNotHaveThirtyTwoDigits(String number) {
		assertThat(validator.isValid(number, null)).isFalse();
	}

}
