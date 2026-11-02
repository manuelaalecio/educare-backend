package com.manuelaalecio.educare_backend.child.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CepTest {

	@ParameterizedTest
	@ValueSource(strings = {"01310-100", "01310100"})
	void shouldAcceptCepWhenItHasEightDigitsWithOrWithoutHyphen(String cep) {
		assertThat(Cep.isValid(cep)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"01310-10", "0131010", "013101000", "0131A-100", "0131-0100", "01310 100", ""})
	void shouldRejectCepWhenFormatIsNotAccepted(String cep) {
		assertThat(Cep.isValid(cep)).isFalse();
	}

	@Test
	void shouldRejectCepWhenNull() {
		assertThat(Cep.isValid(null)).isFalse();
	}

	@Test
	void shouldKeepOnlyDigitsWhenNormalizing() {
		assertThat(Cep.normalize("01310-100")).isEqualTo("01310100");
		assertThat(Cep.normalize("01310100")).isEqualTo("01310100");
	}

	@Test
	void shouldReturnNullWhenNormalizingNull() {
		assertThat(Cep.normalize(null)).isNull();
	}

}
