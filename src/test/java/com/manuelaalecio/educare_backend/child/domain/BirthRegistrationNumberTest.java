package com.manuelaalecio.educare_backend.child.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BirthRegistrationNumberTest {

	private static final String NUMBER = "10453901552013100012021000012321";

	@ParameterizedTest
	@ValueSource(strings = {NUMBER, "104539.01.55.2013.1.00012.021.0000123-21",
			"104539 01 55 2013 1 00012 021 0000123 21", "104539-01-55-2013-1-00012-021-0000123-21"})
	void shouldAcceptNumberWhenItHasThirtyTwoDigitsAfterRemovingSeparators(String number) {
		assertThat(BirthRegistrationNumber.isValid(number)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"1045390155201310001202100001232", "104539015520131000120210000123210",
			"1045390155201310001202100001232X", "104539/01/55/2013/1/00012/021/0000123/21", "...", ""})
	void shouldRejectNumberWhenItDoesNotHaveThirtyTwoDigits(String number) {
		assertThat(BirthRegistrationNumber.isValid(number)).isFalse();
	}

	@Test
	void shouldRejectNumberWhenNull() {
		assertThat(BirthRegistrationNumber.isValid(null)).isFalse();
	}

	@Test
	void shouldRemoveDotsSpacesAndHyphensWhenNormalizing() {
		assertThat(BirthRegistrationNumber.normalize("104539.01.55 2013 1.00012.021.0000123-21")).isEqualTo(NUMBER);
	}

	@Test
	void shouldKeepOtherCharactersWhenNormalizing() {
		assertThat(BirthRegistrationNumber.normalize("12/3X")).isEqualTo("12/3X");
	}

	@Test
	void shouldReturnNullWhenNormalizingNull() {
		assertThat(BirthRegistrationNumber.normalize(null)).isNull();
	}

}
