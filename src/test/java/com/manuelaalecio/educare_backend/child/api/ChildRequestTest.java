package com.manuelaalecio.educare_backend.child.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.child.api.dto.ChildRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChildRequestTest {

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
		var request = request("  Maria Clara  ", "sp", "   ", " 529.982.247-25 ");

		// then
		assertThat(request.name()).isEqualTo("Maria Clara");
		assertThat(request.state()).isEqualTo("SP");
		assertThat(request.religion()).isNull();
		assertThat(request.cpf()).isEqualTo("529.982.247-25");
		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void shouldAcceptNameWhenItHasMaxLengthAfterStripping() {
		assertThat(validator.validate(request(" " + "a".repeat(150) + " ", "SP", null, null))).isEmpty();
	}

	@Test
	void shouldRejectEveryRequiredFieldWhenBodyIsEmpty() {
		// given
		var request = new ChildRequest(null, null, null, null, null, null, "  ", null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null);

		// when / then
		assertThat(invalidFields(request)).containsExactlyInAnyOrder("name", "birthDate", "cep", "address",
				"neighborhood", "addressNumber", "city", "state");
	}

	@Test
	void shouldRejectFieldsWhenTheyBreakTheirRules() {
		// given
		var request = new ChildRequest("a".repeat(151), LocalDate.of(2018, 5, 10), "0131A-100", "Avenida",
				"Bairro", "1", null, "Cidade", "São Paulo", null, "brown", "F", "529.982.247-24", "...", null,
				"DATING", null, null, null, null, "a".repeat(2001), "1045390155201310001202100001232");

		// when / then
		assertThat(invalidFields(request)).containsExactlyInAnyOrder("name", "cep", "state", "race", "gender", "cpf",
				"rg", "maritalStatus", "medicalObservation", "birthCertificateNumber");
	}

	private static ChildRequest request(String name, String state, String religion, String cpf) {
		return new ChildRequest(name, LocalDate.of(2018, 5, 10), "01310-100", "Avenida Paulista", "Bela Vista", "1000",
				null, "São Paulo", state, null, null, null, cpf, null, null, null, religion, null, null, null, null,
				null);
	}

	private static Set<String> invalidFields(ChildRequest request) {
		return validator.validate(request)
			.stream()
			.map(ConstraintViolation::getPropertyPath)
			.map(Object::toString)
			.collect(Collectors.toSet());
	}

}
