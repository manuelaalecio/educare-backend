package com.manuelaalecio.educare_backend.child.domain;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.BIRTH_CERTIFICATE_NUMBER;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aCompleteChild;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.child.ChildDetailsBuilder;
import com.manuelaalecio.educare_backend.shared.error.ConflictException;
import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChildTest {

	@Test
	void shouldKeepRequiredDataAndLeaveOptionalEmptyWhenCreatedWithRequiredDataOnly() {
		// when
		var child = new Child(aChild().build());

		// then
		assertThat(child.getName()).isEqualTo("Maria Clara Souza");
		assertThat(child.getBirthDate()).isEqualTo(LocalDate.of(2018, 5, 10));
		assertThat(child.getCep()).isEqualTo("01310100");
		assertThat(child.getAddress()).isEqualTo("Avenida Paulista");
		assertThat(child.getNeighborhood()).isEqualTo("Bela Vista");
		assertThat(child.getAddressNumber()).isEqualTo("1000");
		assertThat(child.getCity()).isEqualTo("São Paulo");
		assertThat(child.getState()).isEqualTo(BrazilianState.SP);
		assertThat(child).extracting(Child::getAddressComplement, Child::getBirthplace, Child::getRace,
				Child::getGender, Child::getCpf, Child::getRg, Child::getNationality, Child::getMaritalStatus,
				Child::getReligion, Child::getChurch, Child::getHealthReport, Child::getMedication,
				Child::getMedicalObservation, Child::getBirthCertificateNumber)
			.containsOnlyNulls();
	}

	@Test
	void shouldNormalizeDocumentsWhenCreatedWithEveryField() {
		// when
		var child = new Child(aCompleteChild().birthCertificateNumber("104539.01.55.2013.1.00012.021.0000123-21")
			.build());

		// then
		assertThat(child.getCpf()).isEqualTo("52998224725");
		assertThat(child.getRg()).isEqualTo("123456789");
		assertThat(child.getBirthCertificateNumber()).isEqualTo(BIRTH_CERTIFICATE_NUMBER);
		assertThat(child.getAddressComplement()).isEqualTo("Apto 12");
		assertThat(child.getRace()).isEqualTo(Race.BROWN);
		assertThat(child.getGender()).isEqualTo(Gender.FEMALE);
		assertThat(child.getMaritalStatus()).isEqualTo(MaritalStatus.SINGLE);
		assertThat(child.getMedicalObservation()).isEqualTo("Evitar poeira");
	}

	@Test
	void shouldStripEveryTextWhenCreated() {
		// when
		var child = new Child(aChild().name("  Maria Clara Souza  ")
			.cep(" 01310100 ")
			.address(" Avenida Paulista ")
			.neighborhood(" Bela Vista ")
			.addressNumber(" 1000 ")
			.addressComplement(" Apto 12 ")
			.city(" São Paulo ")
			.birthplace(" São Paulo - SP ")
			.cpf(" 52998224725 ")
			.rg(" 12.345.678-x ")
			.nationality(" Brasileira ")
			.religion(" Católica ")
			.church(" Paróquia ")
			.healthReport(" Asma ")
			.medication(" Salbutamol ")
			.medicalObservation(" Poeira ")
			.birthCertificateNumber(" " + BIRTH_CERTIFICATE_NUMBER + " ")
			.build());

		// then
		assertThat(child).extracting(Child::getName, Child::getCep, Child::getAddress, Child::getNeighborhood,
				Child::getAddressNumber, Child::getAddressComplement, Child::getCity, Child::getBirthplace,
				Child::getCpf, Child::getRg, Child::getNationality, Child::getReligion, Child::getChurch,
				Child::getHealthReport, Child::getMedication, Child::getMedicalObservation,
				Child::getBirthCertificateNumber)
			.containsExactly("Maria Clara Souza", "01310100", "Avenida Paulista", "Bela Vista", "1000", "Apto 12",
					"São Paulo", "São Paulo - SP", "52998224725", "12345678X", "Brasileira", "Católica", "Paróquia",
					"Asma", "Salbutamol", "Poeira", BIRTH_CERTIFICATE_NUMBER);
	}

	@Test
	void shouldStoreBlankOptionalTextsAsNullWhenCreated() {
		// when
		var child = new Child(aChild().addressComplement("   ")
			.birthplace("")
			.cpf(" ")
			.rg("  ")
			.nationality(" ")
			.religion("   ")
			.church("\t")
			.healthReport(" ")
			.medication(" ")
			.medicalObservation(" ")
			.birthCertificateNumber("   ")
			.build());

		// then
		assertThat(child).extracting(Child::getAddressComplement, Child::getBirthplace, Child::getCpf, Child::getRg,
				Child::getNationality, Child::getReligion, Child::getChurch, Child::getHealthReport,
				Child::getMedication, Child::getMedicalObservation, Child::getBirthCertificateNumber)
			.containsOnlyNulls();
	}

	@Test
	void shouldKeepSameValuesWhenNormalizedDataIsAppliedAgain() {
		// given
		var child = new Child(aCompleteChild().build());
		ChildDetails normalized = aCompleteChild().cep(child.getCep())
			.cpf(child.getCpf())
			.rg(child.getRg())
			.birthCertificateNumber(child.getBirthCertificateNumber())
			.build();

		// when
		child.update(normalized);

		// then
		assertThat(child.getCep()).isEqualTo("01310100");
		assertThat(child.getCpf()).isEqualTo("52998224725");
		assertThat(child.getRg()).isEqualTo("123456789");
		assertThat(child.getBirthCertificateNumber()).isEqualTo(BIRTH_CERTIFICATE_NUMBER);
	}

	static Stream<Arguments> missingRequiredFields() {
		return Stream.of(
				Arguments.of("Nome", (UnaryOperator<ChildDetailsBuilder>) b -> b.name(null)),
				Arguments.of("Nome", (UnaryOperator<ChildDetailsBuilder>) b -> b.name("   ")),
				Arguments.of("CEP", (UnaryOperator<ChildDetailsBuilder>) b -> b.cep(null)),
				Arguments.of("CEP", (UnaryOperator<ChildDetailsBuilder>) b -> b.cep(" ")),
				Arguments.of("Endereço", (UnaryOperator<ChildDetailsBuilder>) b -> b.address(null)),
				Arguments.of("Bairro", (UnaryOperator<ChildDetailsBuilder>) b -> b.neighborhood("")),
				Arguments.of("Número", (UnaryOperator<ChildDetailsBuilder>) b -> b.addressNumber(" ")),
				Arguments.of("Cidade", (UnaryOperator<ChildDetailsBuilder>) b -> b.city(null)),
				Arguments.of("UF", (UnaryOperator<ChildDetailsBuilder>) b -> b.state(null)),
				Arguments.of("Data de nascimento", (UnaryOperator<ChildDetailsBuilder>) b -> b.birthDate(null)));
	}

	@ParameterizedTest
	@MethodSource("missingRequiredFields")
	void shouldRejectChildWhenRequiredFieldIsMissing(String field, UnaryOperator<ChildDetailsBuilder> change) {
		ChildDetails details = change.apply(aChild()).build();

		assertThatIllegalArgumentException().isThrownBy(() -> new Child(details)).withMessageStartingWith(field);
	}

	@Test
	void shouldAcceptChildWhenOnlyAddressComplementIsMissing() {
		assertThat(new Child(aChild().addressComplement(null).build()).getAddressComplement()).isNull();
	}

	@Test
	void shouldRejectChildWhenDetailsAreNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Child(null));
	}

	static Stream<Arguments> textLimits() {
		return Stream.of(
				Arguments.of("Nome", 150, (TextField) ChildDetailsBuilder::name),
				Arguments.of("Naturalidade", 150, (TextField) ChildDetailsBuilder::birthplace),
				Arguments.of("Endereço", 200, (TextField) ChildDetailsBuilder::address),
				Arguments.of("Bairro", 100, (TextField) ChildDetailsBuilder::neighborhood),
				Arguments.of("Número", 20, (TextField) ChildDetailsBuilder::addressNumber),
				Arguments.of("Complemento", 100, (TextField) ChildDetailsBuilder::addressComplement),
				Arguments.of("Cidade", 100, (TextField) ChildDetailsBuilder::city),
				Arguments.of("RG", 20, (TextField) ChildDetailsBuilder::rg),
				Arguments.of("Nacionalidade", 100, (TextField) ChildDetailsBuilder::nationality),
				Arguments.of("Religião", 100, (TextField) ChildDetailsBuilder::religion),
				Arguments.of("Igreja", 150, (TextField) ChildDetailsBuilder::church),
				Arguments.of("Medicação", 1000, (TextField) ChildDetailsBuilder::medication),
				Arguments.of("Laudo de saúde", 2000, (TextField) ChildDetailsBuilder::healthReport),
				Arguments.of("Observação médica", 2000, (TextField) ChildDetailsBuilder::medicalObservation));
	}

	@ParameterizedTest
	@MethodSource("textLimits")
	void shouldAcceptTextWhenItHasMaxLengthAfterStripping(String field, int max, TextField setter) {
		ChildDetails details = setter.set(aChild(), " " + "a".repeat(max) + " ").build();

		assertThat(new Child(details)).isNotNull();
	}

	@ParameterizedTest
	@MethodSource("textLimits")
	void shouldRejectTextWhenItIsLongerThanMaxLength(String field, int max, TextField setter) {
		ChildDetails details = setter.set(aChild(), "a".repeat(max + 1)).build();

		assertThatIllegalArgumentException().isThrownBy(() -> new Child(details))
			.withMessage(field + " deve ter no máximo " + max + " caracteres");
	}

	@ParameterizedTest
	@ValueSource(strings = {"01310-10", "0131A-100", "123"})
	void shouldRejectChildWhenCepIsInvalid(String cep) {
		ChildDetails details = aChild().cep(cep).build();

		assertThatIllegalArgumentException().isThrownBy(() -> new Child(details)).withMessage("CEP inválido");
	}

	@ParameterizedTest
	@ValueSource(strings = {"529.982.247-24", "11111111111", "5299822472"})
	void shouldRejectChildWhenCpfIsInvalid(String cpf) {
		ChildDetails details = aChild().cpf(cpf).build();

		assertThatIllegalArgumentException().isThrownBy(() -> new Child(details)).withMessage("CPF inválido");
	}

	@Test
	void shouldRejectChildWhenRgHasNoLetterNorDigit() {
		ChildDetails details = aChild().rg("...").build();

		assertThatIllegalArgumentException().isThrownBy(() -> new Child(details))
			.withMessage("RG deve ter ao menos uma letra ou dígito");
	}

	@ParameterizedTest
	@ValueSource(strings = {"1045390155201310001202100001232", "1045390155201310001202100001232X"})
	void shouldRejectChildWhenBirthCertificateNumberDoesNotHaveThirtyTwoDigits(String number) {
		ChildDetails details = aChild().birthCertificateNumber(number).build();

		assertThatIllegalArgumentException().isThrownBy(() -> new Child(details))
			.withMessage("Matrícula da certidão deve ter 32 dígitos");
	}

	@Test
	void shouldReplaceEveryFieldAndClearMissingOptionalsWhenUpdated() {
		// given
		var child = new Child(aCompleteChild().build());

		// when
		child.update(aChild().name("Maria Clara Lima").neighborhood("Consolação").addressNumber("250").build());

		// then
		assertThat(child.getName()).isEqualTo("Maria Clara Lima");
		assertThat(child.getNeighborhood()).isEqualTo("Consolação");
		assertThat(child.getAddressNumber()).isEqualTo("250");
		assertThat(child).extracting(Child::getAddressComplement, Child::getBirthplace, Child::getRace,
				Child::getGender, Child::getCpf, Child::getRg, Child::getNationality, Child::getMaritalStatus,
				Child::getReligion, Child::getChurch, Child::getHealthReport, Child::getMedication,
				Child::getMedicalObservation, Child::getBirthCertificateNumber)
			.containsOnlyNulls();
	}

	@Test
	void shouldKeepPreviousDataWhenUpdateIsInvalid() {
		// given
		var child = new Child(aCompleteChild().build());

		// when / then
		assertThatIllegalArgumentException()
			.isThrownBy(() -> child.update(aChild().name("Outro nome").cep("123").build()));
		assertThat(child.getName()).isEqualTo("Maria Clara Souza");
		assertThat(child.getCpf()).isEqualTo("52998224725");
	}

	@Test
	void shouldNotRepeatValueWhenRejectingDocument() {
		ChildDetails details = aChild().cpf("529.982.247-24").build();

		assertThatIllegalArgumentException().isThrownBy(() -> new Child(details))
			.withMessageNotContaining("529");
	}

	@Test
	void shouldNotCarryPersonalDataWhenExceptionsAreCreated() {
		assertThat(new CpfAlreadyInUseException()).isInstanceOf(ConflictException.class)
			.hasMessage("CPF já cadastrado");
		assertThat(new RgAlreadyInUseException()).isInstanceOf(ConflictException.class)
			.hasMessage("RG já cadastrado");
		assertThat(new BirthCertificateAlreadyInUseException()).isInstanceOf(ConflictException.class)
			.hasMessage("Matrícula da certidão já cadastrada");
		assertThat(new ChildNotFoundException()).hasMessage("Criança não encontrada");
		assertThat(new BirthDateInFutureException()).isInstanceOf(InvalidFieldException.class)
			.hasMessage("Data de nascimento não pode ser futura")
			.extracting(InvalidFieldException::getField)
			.isEqualTo("birthDate");
	}

	@FunctionalInterface
	interface TextField {

		ChildDetailsBuilder set(ChildDetailsBuilder builder, String value);

	}

}
