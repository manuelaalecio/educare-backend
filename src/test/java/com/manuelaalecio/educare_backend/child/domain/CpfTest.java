package com.manuelaalecio.educare_backend.child.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

	@ParameterizedTest
	@ValueSource(strings = {"529.982.247-25", "52998224725", "11144477735", "111.444.777-35", "00000000191"})
	void shouldAcceptCpfWhenCheckDigitsAreRight(String cpf) {
		assertThat(Cpf.isValid(cpf)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"529.982.247-24", "52998224715", "11144477736"})
	void shouldRejectCpfWhenACheckDigitIsWrong(String cpf) {
		assertThat(Cpf.isValid(cpf)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"11111111111", "000.000.000-00", "99999999999"})
	void shouldRejectCpfWhenAllDigitsAreEqual(String cpf) {
		assertThat(Cpf.isValid(cpf)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"529.98224725", "529982247-25", "529-982-247.25", "5299822472", "529982247250",
			"5299822472A", "", " 52998224725"})
	void shouldRejectCpfWhenFormatIsNotAccepted(String cpf) {
		assertThat(Cpf.isValid(cpf)).isFalse();
	}

	@ParameterizedTest
	@NullSource
	void shouldRejectCpfWhenNull(String cpf) {
		assertThat(Cpf.isValid(cpf)).isFalse();
	}

	@Test
	void shouldKeepOnlyDigitsWhenNormalizing() {
		assertThat(Cpf.normalize("529.982.247-25")).isEqualTo("52998224725");
		assertThat(Cpf.normalize("52998224725")).isEqualTo("52998224725");
	}

	@Test
	void shouldReturnNullWhenNormalizingNull() {
		assertThat(Cpf.normalize(null)).isNull();
	}

}
