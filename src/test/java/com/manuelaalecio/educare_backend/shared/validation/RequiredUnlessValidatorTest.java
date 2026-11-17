package com.manuelaalecio.educare_backend.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RequiredUnlessValidatorTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@RequiredUnless(flag = "notWorking", fields = {"workplace", "workAddress", "workPhone"})
	record Sample(Boolean notWorking, String workplace, String workAddress, String workPhone) {
	}

	@RequiredUnless(flag = "notWorking", fields = {"workplace", "missing"})
	record MisconfiguredSample(Boolean notWorking, String workplace) {
	}

	@RequiredUnless(flag = "notWorking", fields = {"workplace"})
	record ThrowingSample(Boolean notWorking, String workplace) {

		@Override
		public String workplace() {
			throw new UnsupportedOperationException("falha no acessor");
		}

	}

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	@Test
	void shouldAcceptMissingFieldsWhenFlagIsTrue() {
		assertThat(validator.validate(new Sample(true, null, " ", null))).isEmpty();
	}

	@Test
	void shouldAcceptWhenFlagIsFalseAndEveryFieldIsFilled() {
		assertThat(validator.validate(new Sample(false, "Padaria", "Rua A, 1", "1133334444"))).isEmpty();
	}

	@Test
	void shouldRejectEachMissingFieldOnItsOwnPathWhenFlagIsFalse() {
		// when
		Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample(false, null, null, null));

		// then
		assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
			.containsExactlyInAnyOrder("workplace", "workAddress", "workPhone");
		assertThat(violations).extracting(ConstraintViolation::getMessage).containsOnly("é obrigatório");
	}

	@Test
	void shouldRequireFieldsWhenFlagIsNull() {
		// when
		Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample(null, null, null, null));

		// then
		assertThat(violations).hasSize(3);
	}

	@Test
	void shouldRejectOnlyTheMissingFieldWhenTheOthersAreFilled() {
		// when
		Set<ConstraintViolation<Sample>> violations = validator
			.validate(new Sample(false, "Padaria", null, "1133334444"));

		// then
		assertThat(violations).singleElement()
			.satisfies(violation -> assertThat(violation.getPropertyPath()).hasToString("workAddress"));
	}

	@Test
	void shouldRejectFieldWhenTextIsOnlySpaces() {
		// when
		Set<ConstraintViolation<Sample>> violations = validator
			.validate(new Sample(false, "   ", "Rua A, 1", "1133334444"));

		// then
		assertThat(violations).singleElement()
			.satisfies(violation -> assertThat(violation.getPropertyPath()).hasToString("workplace"));
	}

	@Test
	void shouldFailWhenRecordDoesNotHaveTheConfiguredComponent() {
		assertThatThrownBy(() -> validator.validate(new MisconfiguredSample(false, "Padaria")))
			.isInstanceOf(ValidationException.class)
			.hasStackTraceContaining("não tem o componente missing");
	}

	@Test
	void shouldFailWhenComponentCannotBeRead() {
		assertThatThrownBy(() -> validator.validate(new ThrowingSample(false, "Padaria")))
			.isInstanceOf(ValidationException.class)
			.hasStackTraceContaining("não foi possível ler o componente workplace");
	}

}
