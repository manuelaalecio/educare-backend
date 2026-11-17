package com.manuelaalecio.educare_backend.guardian.domain;

import static com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder.aCompleteGuardian;
import static com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder.aGuardian;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder;
import com.manuelaalecio.educare_backend.shared.error.ConflictException;
import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GuardianTest {

	@Test
	void shouldKeepRequiredDataAndLeaveOptionalEmptyWhenCreatedWithRequiredDataOnly() {
		// when
		var guardian = new Guardian(aGuardian().build());

		// then
		assertThat(guardian).extracting(Guardian::getName, Guardian::getPhone, Guardian::isNotWorking,
				Guardian::getWorkplace, Guardian::getWorkAddress, Guardian::getWorkPhone)
			.containsExactly("Maria Souza", "11912345678", false, "Padaria Central", "Rua das Flores, 100",
					"1133334444");
		assertThat(guardian).extracting(Guardian::getMaritalStatus, Guardian::getBirthDate, Guardian::getCpf,
				Guardian::getRg)
			.containsOnlyNulls();
	}

	@Test
	void shouldNormalizeDocumentsWhenCreatedWithEveryField() {
		// when
		var guardian = new Guardian(aCompleteGuardian().build());

		// then
		assertThat(guardian.getCpf()).isEqualTo("52998224725");
		assertThat(guardian.getRg()).isEqualTo("12345678X");
		assertThat(guardian.getMaritalStatus()).isEqualTo(MaritalStatus.MARRIED);
		assertThat(guardian.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 10));
	}

	@Test
	void shouldStripEveryTextWhenCreated() {
		// when
		var guardian = new Guardian(aGuardian().name("  Maria Souza  ")
			.cpf(" 52998224725 ")
			.rg(" 12.345.678-x ")
			.phone(" 11912345678 ")
			.workplace(" Padaria Central ")
			.workAddress(" Rua das Flores, 100 ")
			.workPhone(" 1133334444 ")
			.build());

		// then
		assertThat(guardian).extracting(Guardian::getName, Guardian::getCpf, Guardian::getRg, Guardian::getPhone,
				Guardian::getWorkplace, Guardian::getWorkAddress, Guardian::getWorkPhone)
			.containsExactly("Maria Souza", "52998224725", "12345678X", "11912345678", "Padaria Central",
					"Rua das Flores, 100", "1133334444");
	}

	@Test
	void shouldStoreBlankOptionalTextsAsNullWhenCreated() {
		// when
		var guardian = new Guardian(aGuardian().cpf("   ").rg("\t").build());

		// then
		assertThat(guardian).extracting(Guardian::getCpf, Guardian::getRg).containsOnlyNulls();
	}

	@Test
	void shouldDiscardWorkFieldsWhenNotWorking() {
		// when
		var guardian = new Guardian(aGuardian().notWorking(true).workPhone("123").build());

		// then
		assertThat(guardian.isNotWorking()).isTrue();
		assertThat(guardian).extracting(Guardian::getWorkplace, Guardian::getWorkAddress, Guardian::getWorkPhone)
			.containsOnlyNulls();
	}

	@Test
	void shouldAcceptMissingWorkFieldsWhenNotWorking() {
		// when
		var guardian = new Guardian(aGuardian().notWorking(true).workplace(null).workAddress(null).workPhone(null)
			.build());

		// then
		assertThat(guardian.isNotWorking()).isTrue();
	}

	@Test
	void shouldKeepSameValuesWhenNormalizedDataIsAppliedAgain() {
		// given
		var guardian = new Guardian(aCompleteGuardian().build());
		var normalized = aCompleteGuardian().cpf(guardian.getCpf()).rg(guardian.getRg()).build();

		// when
		guardian.update(normalized);

		// then
		assertThat(guardian.getCpf()).isEqualTo("52998224725");
		assertThat(guardian.getRg()).isEqualTo("12345678X");
	}

	@ParameterizedTest
	@ValueSource(strings = {"1133334444", "11912345678"})
	void shouldAcceptPhonesWhenTheyHaveTenOrElevenDigits(String phone) {
		// when
		var guardian = new Guardian(aGuardian().phone(phone).workPhone(phone).build());

		// then
		assertThat(guardian.getPhone()).isEqualTo(phone);
		assertThat(guardian.getWorkPhone()).isEqualTo(phone);
	}

	@Test
	void shouldAcceptTextsWhenTheyHaveExactlyTheMaximumLength() {
		// when
		var guardian = new Guardian(aGuardian().name("a".repeat(150))
			.workplace("b".repeat(150))
			.workAddress("c".repeat(200))
			.rg("1".repeat(20))
			.build());

		// then
		assertThat(guardian.getName()).hasSize(150);
		assertThat(guardian.getWorkplace()).hasSize(150);
		assertThat(guardian.getWorkAddress()).hasSize(200);
		assertThat(guardian.getRg()).hasSize(20);
	}

	@Test
	void shouldRejectWhenDetailsAreNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Guardian(null))
			.withMessage("Dados do responsável são obrigatórios");
	}

	static Stream<Arguments> invalidDetails() {
		return Stream.of(invalid("Nome é obrigatório", b -> b.name(null)),
				invalid("Nome é obrigatório", b -> b.name("   ")),
				invalid("Nome deve ter no máximo 150 caracteres", b -> b.name("a".repeat(151))),
				invalid("Telefone é obrigatório", b -> b.phone(null)),
				invalid("Telefone é obrigatório", b -> b.phone(" ")),
				invalid("Telefone deve ter 10 ou 11 dígitos", b -> b.phone("113333444")),
				invalid("Telefone deve ter 10 ou 11 dígitos", b -> b.phone("119123456789")),
				invalid("Telefone deve ter 10 ou 11 dígitos", b -> b.phone("(11) 3333-4444")),
				invalid("Local de trabalho é obrigatório", b -> b.workplace(null)),
				invalid("Local de trabalho é obrigatório", b -> b.workplace("  ")),
				invalid("Local de trabalho deve ter no máximo 150 caracteres", b -> b.workplace("a".repeat(151))),
				invalid("Endereço do trabalho é obrigatório", b -> b.workAddress(null)),
				invalid("Endereço do trabalho deve ter no máximo 200 caracteres",
						b -> b.workAddress("a".repeat(201))),
				invalid("Telefone do trabalho é obrigatório", b -> b.workPhone(null)),
				invalid("Telefone do trabalho deve ter 10 ou 11 dígitos", b -> b.workPhone("1133")),
				invalid("CPF inválido", b -> b.cpf("123.456.789-00")),
				invalid("CPF inválido", b -> b.cpf("11111111111")),
				invalid("RG deve ter no máximo 20 caracteres", b -> b.rg("1".repeat(21))),
				invalid("RG deve ter ao menos uma letra ou dígito", b -> b.rg("...")));
	}

	private static Arguments invalid(String message, UnaryOperator<GuardianDetailsBuilder> change) {
		return Arguments.of(message, change);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidDetails")
	void shouldRejectWithMessageWithoutValueWhenFieldIsInvalid(String message,
			UnaryOperator<GuardianDetailsBuilder> change) {
		// given
		var details = change.apply(aGuardian()).build();

		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> new Guardian(details)).withMessage(message);
	}

	@Test
	void shouldNotChangeAnythingWhenUpdateIsRefused() {
		// given
		var guardian = new Guardian(aCompleteGuardian().build());
		var invalid = aGuardian().name("Outro Nome").notWorking(true).cpf(null).rg("...").build();

		// when
		assertThatIllegalArgumentException().isThrownBy(() -> guardian.update(invalid));

		// then
		assertThat(guardian).extracting(Guardian::getName, Guardian::getCpf, Guardian::getRg, Guardian::isNotWorking,
				Guardian::getWorkplace, Guardian::getMaritalStatus)
			.containsExactly("Maria Souza", "52998224725", "12345678X", false, "Padaria Central",
					MaritalStatus.MARRIED);
	}

	@Test
	void shouldReplaceEveryFieldAndClearOptionalOnesWhenUpdated() {
		// given
		var guardian = new Guardian(aCompleteGuardian().build());

		// when
		guardian.update(aGuardian().name("Maria Souza Lima").phone("1144445555").notWorking(true).build());

		// then
		assertThat(guardian.getName()).isEqualTo("Maria Souza Lima");
		assertThat(guardian.getPhone()).isEqualTo("1144445555");
		assertThat(guardian.isNotWorking()).isTrue();
		assertThat(guardian).extracting(Guardian::getMaritalStatus, Guardian::getBirthDate, Guardian::getCpf,
				Guardian::getRg, Guardian::getWorkplace, Guardian::getWorkAddress, Guardian::getWorkPhone)
			.containsOnlyNulls();
	}

	@Test
	void shouldDescribeErrorsWithoutPersonalDataWhenExceptionsAreCreated() {
		// when
		var conflict = new GuardianCpfAlreadyInUseException();
		var future = new GuardianBirthDateInFutureException();
		var notFound = new GuardianNotFoundException();

		// then
		assertThat(conflict).isInstanceOf(ConflictException.class);
		assertThat(conflict.getField()).isEqualTo("cpf");
		assertThat(conflict).hasMessage("Já existe um responsável com este CPF.");
		assertThat(future).isInstanceOf(InvalidFieldException.class);
		assertThat(future.getField()).isEqualTo("birthDate");
		assertThat(notFound).hasMessage("Responsável não encontrado");
	}

}
