package com.manuelaalecio.educare_backend.guardian.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuardianRequestTest {

	private static ValidatorFactory factory;
	private static Validator validator;

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
	void shouldStripTextsAndTurnBlankOptionalsIntoNullWhenCreated() {
		// when
		var request = new GuardianRequest("  Maria Souza ", " ", null, " 529.982.247-25 ", "   ", " 11912345678 ",
				false, " Padaria Central ", " Rua das Flores, 100 ", " 1133334444 ");

		// then
		assertThat(request.name()).isEqualTo("Maria Souza");
		assertThat(request.maritalStatus()).isNull();
		assertThat(request.cpf()).isEqualTo("529.982.247-25");
		assertThat(request.rg()).isNull();
		assertThat(request.phone()).isEqualTo("11912345678");
		assertThat(request.workplace()).isEqualTo("Padaria Central");
		assertThat(request.workAddress()).isEqualTo("Rua das Flores, 100");
		assertThat(request.workPhone()).isEqualTo("1133334444");
		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void shouldDiscardWorkFieldsWhenNotWorking() {
		// when
		var request = new GuardianRequest("Maria Souza", null, null, null, null, "11912345678", true, "Padaria",
				"x".repeat(300), "123");

		// then
		assertThat(request.workplace()).isNull();
		assertThat(request.workAddress()).isNull();
		assertThat(request.workPhone()).isNull();
		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void shouldRequireEveryRequiredFieldWhenBodyIsEmpty() {
		// when
		Set<String> fields = fields(new GuardianRequest(null, null, null, null, null, null, null, null, " ", null));

		// then
		assertThat(fields).containsExactlyInAnyOrder("name", "phone", "workplace", "workAddress", "workPhone");
	}

	@Test
	void shouldAcceptTextsWhenTheyHaveMaxLengthAfterStripping() {
		// when
		var request = new GuardianRequest(" " + "a".repeat(150) + " ", null, null, null, "1".repeat(20),
				"1133334444", false, "b".repeat(150), "c".repeat(200), "11987654321");

		// then
		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void shouldRejectTextsWhenTheyAreTooLong() {
		// when
		Set<String> fields = fields(new GuardianRequest("a".repeat(151), null, null, null, "1".repeat(21),
				"1133334444", false, "b".repeat(151), "c".repeat(201), "1133334444"));

		// then
		assertThat(fields).containsExactlyInAnyOrder("name", "rg", "workplace", "workAddress");
	}

	private static Set<String> fields(GuardianRequest request) {
		return validator.validate(request)
			.stream()
			.map(ConstraintViolation::getPropertyPath)
			.map(Object::toString)
			.collect(Collectors.toSet());
	}

}
