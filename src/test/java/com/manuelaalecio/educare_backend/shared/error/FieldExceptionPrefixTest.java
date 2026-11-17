package com.manuelaalecio.educare_backend.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldExceptionPrefixTest {

	@Test
	void shouldPrefixFieldAndKeepMessageWhenInvalidFieldIsNested() {
		// given
		InvalidFieldException original = new SampleInvalidFieldException();

		// when
		InvalidFieldException nested = original.withFieldPrefix("guardians[1].guardian.");

		// then
		assertThat(nested).isInstanceOf(InvalidFieldException.class).hasCause(original);
		assertThat(nested.getField()).isEqualTo("guardians[1].guardian.birthDate");
		assertThat(nested.getMessage()).isEqualTo("Data de nascimento não pode ser futura");
		assertThat(original.getField()).isEqualTo("birthDate");
	}

	@Test
	void shouldPrefixFieldAndKeepMessageWhenFieldConflictIsNested() {
		// given
		FieldConflictException original = new SampleFieldConflictException();

		// when
		FieldConflictException nested = original.withFieldPrefix("guardians[1].guardian.");

		// then
		assertThat(nested).isInstanceOf(ConflictException.class).hasCause(original);
		assertThat(nested.getField()).isEqualTo("guardians[1].guardian.cpf");
		assertThat(nested.getMessage()).isEqualTo("Já existe um responsável com este CPF.");
		assertThat(original.getField()).isEqualTo("cpf");
	}

	@Test
	void shouldAccumulatePrefixesWhenNestedTwice() {
		// when
		FieldConflictException nested = new SampleFieldConflictException().withFieldPrefix("guardian.")
			.withFieldPrefix("guardians[0].");

		// then
		assertThat(nested.getField()).isEqualTo("guardians[0].guardian.cpf");
	}

	static class SampleInvalidFieldException extends InvalidFieldException {

		SampleInvalidFieldException() {
			super("birthDate", "Data de nascimento não pode ser futura");
		}

	}

	static class SampleFieldConflictException extends FieldConflictException {

		SampleFieldConflictException() {
			super("cpf", "Já existe um responsável com este CPF.");
		}

	}

}
