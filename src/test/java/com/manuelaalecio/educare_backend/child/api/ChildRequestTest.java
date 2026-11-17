package com.manuelaalecio.educare_backend.child.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.child.api.dto.ChildGuardianRequest;
import com.manuelaalecio.educare_backend.child.api.dto.ChildRequest;
import com.manuelaalecio.educare_backend.child.api.dto.NewGuardianRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChildRequestTest {

	private static final UUID CLASSROOM_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final UUID MARIA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID JOAO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d2");
	private static final NewGuardianRequest MARIA = new NewGuardianRequest("Maria Souza", null, null, null, null,
			"11912345678", false, "Padaria Central", "Rua das Flores, 100", "1133334444");
	private static final List<ChildGuardianRequest> GUARDIANS = List
		.of(new ChildGuardianRequest(MARIA_ID, null, "MOTHER"));

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
				null, null, null, null, null, null, null, null, null, null, null);

		// when / then
		assertThat(invalidFields(request)).containsExactlyInAnyOrder("name", "birthDate", "cep", "address",
				"neighborhood", "addressNumber", "city", "state", "classroomId", "guardians");
	}

	@Test
	void shouldRejectFieldsWhenTheyBreakTheirRules() {
		// given
		var request = new ChildRequest("a".repeat(151), LocalDate.of(2018, 5, 10), "0131A-100", "Avenida",
				"Bairro", "1", null, "Cidade", "São Paulo", null, "brown", "F", "529.982.247-24", "...", null,
				"DATING", null, null, null, null, "a".repeat(2001), "1045390155201310001202100001232",
				CLASSROOM_ID, GUARDIANS);

		// when / then
		assertThat(invalidFields(request)).containsExactlyInAnyOrder("name", "cep", "state", "race", "gender", "cpf",
				"rg", "maritalStatus", "medicalObservation", "birthCertificateNumber");
	}

	@Test
	void shouldAcceptGuardiansWhenThereAreFiveValidItems() {
		// given
		List<ChildGuardianRequest> guardians = List.of(new ChildGuardianRequest(MARIA_ID, null, "MOTHER"),
				new ChildGuardianRequest(JOAO_ID, null, "FATHER"), new ChildGuardianRequest(null, MARIA, "AUNT"),
				new ChildGuardianRequest(null, MARIA, "UNCLE"), new ChildGuardianRequest(null, MARIA, "OTHER"));

		// when / then
		assertThat(validator.validate(withGuardians(guardians))).isEmpty();
	}

	@Test
	void shouldRequireAtLeastOneGuardianWhenListIsMissingOrEmpty() {
		assertThat(messagesOf(withGuardians(null), "guardians")).containsExactly("Informe pelo menos um responsável.");
		assertThat(messagesOf(withGuardians(List.of()), "guardians"))
			.containsExactly("Informe pelo menos um responsável.");
	}

	@Test
	void shouldRejectListWhenItHasMoreThanFiveItems() {
		// given
		List<ChildGuardianRequest> guardians = Collections.nCopies(6, new ChildGuardianRequest(null, MARIA, "OTHER"));

		// when / then
		assertThat(messagesOf(withGuardians(guardians), "guardians"))
			.containsExactly("Informe no máximo 5 responsáveis.");
	}

	@Test
	void shouldRejectListWhenGuardianIdIsRepeated() {
		// given
		List<ChildGuardianRequest> guardians = List.of(new ChildGuardianRequest(MARIA_ID, null, "MOTHER"),
				new ChildGuardianRequest(MARIA_ID, null, "OTHER"));

		// when / then
		assertThat(messagesOf(withGuardians(guardians), "guardians"))
			.containsExactly("O mesmo responsável não pode ser informado mais de uma vez.");
	}

	@Test
	void shouldPointToItemWhenItIsNullOrHasNoneOrBothSources() {
		// given
		List<ChildGuardianRequest> guardians = Arrays.asList(null, new ChildGuardianRequest(null, null, "MOTHER"),
				new ChildGuardianRequest(MARIA_ID, MARIA, "FATHER"));

		// when / then
		assertThat(invalidFields(withGuardians(guardians))).containsExactlyInAnyOrder("guardians[0]", "guardians[1]",
				"guardians[2]");
	}

	@Test
	void shouldPointToRelationshipWhenItIsMissingOrNotInList() {
		// given
		List<ChildGuardianRequest> guardians = List.of(new ChildGuardianRequest(null, MARIA, "  "),
				new ChildGuardianRequest(null, MARIA, "COUSIN"), new ChildGuardianRequest(JOAO_ID, null, "mother"));

		// when / then
		assertThat(invalidFields(withGuardians(guardians))).containsExactlyInAnyOrder("guardians[0].relationship",
				"guardians[1].relationship", "guardians[2].relationship");
	}

	@Test
	void shouldPointToNewGuardianFieldsWithItemPathWhenTheyAreInvalid() {
		// given
		var invalid = new NewGuardianRequest("", null, null, "123.456.789-00", null, "111234", true, null, null, null);
		List<ChildGuardianRequest> guardians = List.of(new ChildGuardianRequest(MARIA_ID, null, "FATHER"),
				new ChildGuardianRequest(null, invalid, "MOTHER"));

		// when / then
		assertThat(invalidFields(withGuardians(guardians))).containsExactlyInAnyOrder("guardians[1].guardian.name",
				"guardians[1].guardian.phone", "guardians[1].guardian.cpf");
	}

	@Test
	void shouldStripRelationshipWhenCreated() {
		assertThat(new ChildGuardianRequest(MARIA_ID, null, " MOTHER ").relationship()).isEqualTo("MOTHER");
		assertThat(new ChildGuardianRequest(MARIA_ID, null, " ").relationship()).isNull();
	}

	private static ChildRequest request(String name, String state, String religion, String cpf) {
		return new ChildRequest(name, LocalDate.of(2018, 5, 10), "01310-100", "Avenida Paulista", "Bela Vista", "1000",
				null, "São Paulo", state, null, null, null, cpf, null, null, null, religion, null, null, null, null,
				null, CLASSROOM_ID, GUARDIANS);
	}

	private static ChildRequest withGuardians(List<ChildGuardianRequest> guardians) {
		return new ChildRequest("Maria Clara", LocalDate.of(2018, 5, 10), "01310-100", "Avenida Paulista",
				"Bela Vista", "1000", null, "São Paulo", "SP", null, null, null, null, null, null, null, null, null,
				null, null, null, null, CLASSROOM_ID, guardians);
	}

	private static Set<String> messagesOf(ChildRequest request, String field) {
		return validator.validate(request)
			.stream()
			.filter(violation -> violation.getPropertyPath().toString().equals(field))
			.map(ConstraintViolation::getMessage)
			.collect(Collectors.toSet());
	}

	/**
	 * The paths as Spring reports them: Hibernate Validator names a list element {@code guardians[0].<list element>},
	 * which becomes {@code guardians[0]} in the {@code errors} of the response.
	 */
	private static Set<String> invalidFields(ChildRequest request) {
		return validator.validate(request)
			.stream()
			.map(ConstraintViolation::getPropertyPath)
			.map(path -> path.toString().replace(".<list element>", ""))
			.collect(Collectors.toSet());
	}

}
