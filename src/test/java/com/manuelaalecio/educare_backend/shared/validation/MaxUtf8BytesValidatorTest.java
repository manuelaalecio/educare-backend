package com.manuelaalecio.educare_backend.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	record Sample(@MaxUtf8Bytes(72) String value) {
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
	void shouldAcceptWhenValueIsNull() {
		assertThat(validator.validate(new Sample(null))).isEmpty();
	}

	@Test
	void shouldAcceptWhenValueHasExactlyMaxBytes() {
		assertThat(validator.validate(new Sample("a".repeat(72)))).isEmpty();
	}

	@Test
	void shouldRejectWhenValueHasOneByteOverMax() {
		// when
		Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample("a".repeat(73)));

		// then
		assertThat(violations).singleElement()
				.satisfies(violation -> {
					assertThat(violation.getPropertyPath()).hasToString("value");
					assertThat(violation.getMessage()).isEqualTo("deve ocupar no máximo 72 bytes em UTF-8");
				});
	}

	@Test
	void shouldRejectWhenMultiByteCharactersExceedMaxBytes() {
		// given: 40 characters, 80 bytes in UTF-8
		String value = "ç".repeat(40);

		// when / then
		assertThat(validator.validate(new Sample(value))).hasSize(1);
	}

}
