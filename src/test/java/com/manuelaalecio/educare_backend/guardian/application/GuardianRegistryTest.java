package com.manuelaalecio.educare_backend.guardian.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianBirthDateInFutureException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianDetails;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianRepository;
import com.manuelaalecio.educare_backend.guardian.domain.MaritalStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class GuardianRegistryTest {

	private static final UUID MARIA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID JOAO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d2");
	/** 01:00 UTC on March 2nd is still March 1st in São Paulo. */
	private static final Instant NOW = Instant.parse("2026-03-02T01:00:00Z");

	private final GuardianRepository guardianRepository = mock(GuardianRepository.class);

	private final GuardianRegistry registry = new GuardianRegistry(guardianRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void shouldSaveNormalizedGuardianAndReturnIdWhenDataIsValid() {
		// given
		givenSavedWithId(MARIA_ID);

		// when
		UUID id = registry.create(input("529.982.247-25", null, "MARRIED", null));

		// then
		assertThat(id).isEqualTo(MARIA_ID);
		Guardian saved = saved();
		assertThat(saved.getName()).isEqualTo("Maria Souza");
		assertThat(saved.getCpf()).isEqualTo("52998224725");
		assertThat(saved.getMaritalStatus()).isEqualTo(MaritalStatus.MARRIED);
		assertThat(saved.isNotWorking()).isFalse();
		verify(guardianRepository).existsByCpf("52998224725");
	}

	@Test
	void shouldNotCheckCpfWhenItIsMissing() {
		// given
		givenSavedWithId(MARIA_ID);

		// when
		registry.create(input(null, null, null, null));

		// then
		verify(guardianRepository, never()).existsByCpf(any());
		assertThat(saved().getMaritalStatus()).isNull();
	}

	@Test
	void shouldDiscardWorkFieldsWhenNotWorkingIsTrue() {
		// given
		givenSavedWithId(MARIA_ID);

		// when
		registry.create(new GuardianInput("Maria Souza", null, null, null, null, "11912345678", true, "Padaria",
				null, null));

		// then
		assertThat(saved().isNotWorking()).isTrue();
		assertThat(saved().getWorkplace()).isNull();
	}

	@Test
	void shouldAcceptBirthDateWhenItIsTodayInSaoPaulo() {
		// given
		givenSavedWithId(MARIA_ID);

		// when
		registry.create(input(null, LocalDate.of(2026, 3, 1), null, null));

		// then
		assertThat(saved().getBirthDate()).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	@Test
	void shouldRejectBirthDateWithoutSavingWhenItIsAfterTodayInSaoPaulo() {
		// when / then
		assertThatThrownBy(() -> registry.create(input(null, LocalDate.of(2026, 3, 2), null, null)))
			.isInstanceOf(GuardianBirthDateInFutureException.class);
		verifyNoInteractions(guardianRepository);
	}

	@Test
	void shouldRejectCpfWithoutSavingWhenAnotherGuardianHasIt() {
		// given
		when(guardianRepository.existsByCpf("52998224725")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> registry.create(input("52998224725", null, null, null)))
			.isInstanceOf(GuardianCpfAlreadyInUseException.class);
		verify(guardianRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldConvertViolationToConflictWhenConcurrentSaveRepeatsCpf() {
		// given
		when(guardianRepository.saveAndFlush(any(Guardian.class))).thenThrow(violation("guardians_cpf_key"));

		// when / then
		assertThatThrownBy(() -> registry.create(input("52998224725", null, null, null)))
			.isInstanceOf(GuardianCpfAlreadyInUseException.class);
	}

	@Test
	void shouldRethrowViolationWhenItIsNotTheCpfIndex() {
		// given
		DataIntegrityViolationException other = violation("guardians_work_check");
		when(guardianRepository.saveAndFlush(any(Guardian.class))).thenThrow(other);

		// when / then
		assertThatThrownBy(() -> registry.create(input(null, null, null, null))).isSameAs(other);
	}

	@Test
	void shouldRethrowViolationWhenItHasNoConstraintName() {
		// given
		var other = new DataIntegrityViolationException("sem constraint");
		when(guardianRepository.saveAndFlush(any(Guardian.class))).thenThrow(other);

		// when / then
		assertThatThrownBy(() -> registry.create(input(null, null, null, null))).isSameAs(other);
	}

	@Test
	void shouldReturnContactsOfExistingGuardiansWhenSearchingSeveralIds() {
		// given
		Guardian maria = withId(new Guardian(new GuardianDetails("Maria Souza", null, null, null, null, "11912345678",
				false, "Padaria Central", "Rua das Flores, 100", "1133334444")), MARIA_ID);
		when(guardianRepository.findAllById(List.of(MARIA_ID, JOAO_ID))).thenReturn(List.of(maria));

		// when
		Map<UUID, GuardianContact> contacts = registry.findContacts(List.of(MARIA_ID, JOAO_ID));

		// then
		assertThat(contacts).containsOnly(Map.entry(MARIA_ID, new GuardianContact(MARIA_ID, "Maria Souza",
				"11912345678", false, "Padaria Central", "1133334444")));
	}

	@Test
	void shouldNotQueryWhenNoIdIsGiven() {
		// when / then
		assertThat(registry.findContacts(List.of())).isEmpty();
		verifyNoInteractions(guardianRepository);
	}

	private static GuardianInput input(String cpf, LocalDate birthDate, String maritalStatus, Boolean notWorking) {
		return new GuardianInput("  Maria Souza ", maritalStatus, birthDate, cpf, null, "11912345678", notWorking,
				"Padaria Central", "Rua das Flores, 100", "1133334444");
	}

	private void givenSavedWithId(UUID id) {
		when(guardianRepository.saveAndFlush(any(Guardian.class)))
			.thenAnswer(invocation -> withId(invocation.getArgument(0), id));
	}

	private Guardian saved() {
		ArgumentCaptor<Guardian> captor = ArgumentCaptor.forClass(Guardian.class);
		verify(guardianRepository).saveAndFlush(captor.capture());
		return captor.getValue();
	}

	static Guardian withId(Guardian guardian, UUID id) {
		ReflectionTestUtils.setField(guardian, "id", id);
		return guardian;
	}

	static DataIntegrityViolationException violation(String constraintName) {
		var cause = new ConstraintViolationException("violation", new SQLException("violation"), "statement",
				constraintName);
		return new DataIntegrityViolationException("could not execute statement", cause);
	}

}
