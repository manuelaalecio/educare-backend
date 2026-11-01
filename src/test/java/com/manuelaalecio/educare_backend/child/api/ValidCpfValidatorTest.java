package com.manuelaalecio.educare_backend.child.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidCpfValidatorTest {

	private final ValidCpfValidator validator = new ValidCpfValidator();

	@Test
	void shouldAcceptCpfWhenNull() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"529.982.247-25", "52998224725"})
	void shouldAcceptCpfWhenItIsValid(String cpf) {
		assertThat(validator.isValid(cpf, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"529.982.247-24", "11111111111", "529.98224725", "abc"})
	void shouldRejectCpfWhenItIsInvalid(String cpf) {
		assertThat(validator.isValid(cpf, null)).isFalse();
	}

}
