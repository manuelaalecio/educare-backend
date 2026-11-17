package com.manuelaalecio.educare_backend.guardian.application;

import static com.manuelaalecio.educare_backend.guardian.application.GuardianRegistryTest.violation;
import static com.manuelaalecio.educare_backend.guardian.application.GuardianRegistryTest.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianBirthDateInFutureException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianDetails;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianNotFoundException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianRepository;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianSpecifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class GuardianServiceTest {

	private static final UUID MARIA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID JOAO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d2");
	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final GuardianChildRef ANA = new GuardianChildRef(ANA_ID, "Ana Souza", "MOTHER");
	/** 01:00 UTC on March 2nd is still March 1st in São Paulo. */
	private static final Instant NOW = Instant.parse("2026-03-02T01:00:00Z");
	private static final Pageable PAGEABLE = PageRequest.of(0, 20);

	private final GuardianRepository guardianRepository = mock(GuardianRepository.class);
	private final GuardianChildrenFinder childrenFinder = mock(GuardianChildrenFinder.class);

	private final GuardianService service = new GuardianService(guardianRepository, childrenFinder,
			Clock.fixed(NOW, ZoneOffset.UTC));

	// ---------- list ----------

	@Test
	void shouldFilterByNameWhenSearchIsText() {
		try (MockedStatic<GuardianSpecifications> specs = mockStatic(GuardianSpecifications.class,
				CALLS_REAL_METHODS)) {
			// given
			givenPage(List.of());

			// when
			service.list("  joão  ", PAGEABLE);

			// then
			specs.verify(() -> GuardianSpecifications.nameMatches("%joão%"));
			specs.verify(() -> GuardianSpecifications.cpfEquals(anyString()), never());
		}
	}

	@Test
	void shouldFilterByExactCpfWhenSearchHasElevenDigits() {
		try (MockedStatic<GuardianSpecifications> specs = mockStatic(GuardianSpecifications.class,
				CALLS_REAL_METHODS)) {
			// given
			givenPage(List.of());

			// when
			service.list(" 52998224725 ", PAGEABLE);

			// then
			specs.verify(() -> GuardianSpecifications.cpfEquals("52998224725"));
			specs.verify(() -> GuardianSpecifications.nameMatches(anyString()), never());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"529982", "529982247250", "529.982.247-25"})
	void shouldFilterByNameWhenSearchIsNotAFullCpf(String q) {
		try (MockedStatic<GuardianSpecifications> specs = mockStatic(GuardianSpecifications.class,
				CALLS_REAL_METHODS)) {
			// given
			givenPage(List.of());

			// when
			service.list(q, PAGEABLE);

			// then
			specs.verify(() -> GuardianSpecifications.nameMatches(anyString()));
			specs.verify(() -> GuardianSpecifications.cpfEquals(anyString()), never());
		}
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void shouldNotFilterWhenSearchIsBlank(String q) {
		try (MockedStatic<GuardianSpecifications> specs = mockStatic(GuardianSpecifications.class,
				CALLS_REAL_METHODS)) {
			// given
			givenPage(List.of());

			// when
			service.list(q, PAGEABLE);

			// then
			specs.verifyNoInteractions();
		}
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"joao | %joao%",
			"% | %\\%%",
			"a_b | %a\\_b%",
			"a\\b | %a\\\\b%"})
	void shouldEscapeSearchPatternWhenNameIsGiven(String text, String pattern) {
		assertThat(GuardianService.likePattern(text)).isEqualTo(pattern);
	}

	@Test
	void shouldNotAskForChildrenWhenPageIsEmpty() {
		// given
		givenPage(List.of());

		// when
		Page<GuardianSummaryView> page = service.list(null, PAGEABLE);

		// then
		assertThat(page.getContent()).isEmpty();
		verifyNoInteractions(childrenFinder);
	}

	@Test
	void shouldAskForChildrenOfThePageInOneCallWhenListing() {
		// given
		Guardian maria = guardian("Maria Souza", MARIA_ID);
		Guardian joao = guardian("João Souza", JOAO_ID);
		givenPage(List.of(joao, maria));
		when(childrenFinder.findChildren(List.of(JOAO_ID, MARIA_ID))).thenReturn(Map.of(MARIA_ID, List.of(ANA)));

		// when
		Page<GuardianSummaryView> page = service.list(null, PAGEABLE);

		// then
		assertThat(page.getContent()).extracting(GuardianSummaryView::guardian).containsExactly(joao, maria);
		assertThat(page.getContent()).extracting(GuardianSummaryView::children).containsExactly(List.of(),
				List.of(ANA));
		verify(childrenFinder, times(1)).findChildren(any());
	}

	// ---------- findById ----------

	@Test
	void shouldReturnGuardianWithChildrenWhenItHasLinks() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));
		when(childrenFinder.findChildren(List.of(MARIA_ID))).thenReturn(Map.of(MARIA_ID, List.of(ANA)));

		// when
		GuardianView view = service.findById(MARIA_ID);

		// then
		assertThat(view.guardian()).isSameAs(maria);
		assertThat(view.children()).containsExactly(ANA);
	}

	@Test
	void shouldReturnEmptyChildrenWhenGuardianHasNoLinks() {
		// given
		givenStored(guardian("Maria Souza", MARIA_ID));
		when(childrenFinder.findChildren(List.of(MARIA_ID))).thenReturn(Map.of());

		// when / then
		assertThat(service.findById(MARIA_ID).children()).isEmpty();
	}

	@Test
	void shouldFailWithNotFoundWhenGuardianDoesNotExist() {
		// given
		when(guardianRepository.findById(MARIA_ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.findById(MARIA_ID)).isInstanceOf(GuardianNotFoundException.class);
	}

	// ---------- update ----------

	@Test
	void shouldReplaceDataAndReturnChildrenWhenUpdateIsValid() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));
		when(guardianRepository.saveAndFlush(maria)).thenReturn(maria);
		when(childrenFinder.findChildren(List.of(MARIA_ID))).thenReturn(Map.of(MARIA_ID, List.of(ANA)));

		// when
		GuardianView view = service.update(MARIA_ID, input("Maria Souza Lima", "529.982.247-25", null));

		// then
		assertThat(view.guardian().getName()).isEqualTo("Maria Souza Lima");
		assertThat(view.guardian().getCpf()).isEqualTo("52998224725");
		assertThat(view.children()).containsExactly(ANA);
		verify(guardianRepository).existsByCpfAndIdNot("52998224725", MARIA_ID);
	}

	@Test
	void shouldAcceptOwnCpfWhenNoOtherGuardianHasIt() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));
		when(guardianRepository.existsByCpfAndIdNot("52998224725", MARIA_ID)).thenReturn(false);
		when(guardianRepository.saveAndFlush(maria)).thenReturn(maria);
		when(childrenFinder.findChildren(any())).thenReturn(Map.of());

		// when
		GuardianView view = service.update(MARIA_ID, input("Maria Souza", "52998224725", null));

		// then
		assertThat(view.guardian().getCpf()).isEqualTo("52998224725");
	}

	@Test
	void shouldRejectCpfWithoutChangingWhenAnotherGuardianHasIt() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));
		when(guardianRepository.existsByCpfAndIdNot("52998224725", MARIA_ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> service.update(MARIA_ID, input("Maria Souza Lima", "52998224725", null)))
			.isInstanceOf(GuardianCpfAlreadyInUseException.class);
		assertThat(maria.getName()).isEqualTo("Maria Souza");
		assertThat(maria.getCpf()).isNull();
		verify(guardianRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldNotCheckCpfWhenItIsMissing() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));
		when(guardianRepository.saveAndFlush(maria)).thenReturn(maria);
		when(childrenFinder.findChildren(any())).thenReturn(Map.of());

		// when
		service.update(MARIA_ID, input("Maria Souza", null, null));

		// then
		verify(guardianRepository, never()).existsByCpfAndIdNot(any(), any());
	}

	@Test
	void shouldConvertViolationToConflictWhenConcurrentUpdateRepeatsCpf() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));
		when(guardianRepository.saveAndFlush(maria)).thenThrow(violation("guardians_cpf_key"));

		// when / then
		assertThatThrownBy(() -> service.update(MARIA_ID, input("Maria Souza", "52998224725", null)))
			.isInstanceOf(GuardianCpfAlreadyInUseException.class);
	}

	@Test
	void shouldSaveNothingWhenUpdatedGuardianDoesNotExist() {
		// given
		when(guardianRepository.findById(MARIA_ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.update(MARIA_ID, input("Maria Souza", null, null)))
			.isInstanceOf(GuardianNotFoundException.class);
		verify(guardianRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectBirthDateWithoutChangingWhenItIsInTheFuture() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));

		// when / then
		assertThatThrownBy(() -> service.update(MARIA_ID, input("Maria Souza Lima", null, LocalDate.of(2026, 3, 2))))
			.isInstanceOf(GuardianBirthDateInFutureException.class);
		assertThat(maria.getName()).isEqualTo("Maria Souza");
		verify(guardianRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldAcceptBirthDateWhenItIsTodayInSaoPaulo() {
		// given
		Guardian maria = givenStored(guardian("Maria Souza", MARIA_ID));
		when(guardianRepository.saveAndFlush(maria)).thenReturn(maria);
		when(childrenFinder.findChildren(any())).thenReturn(Map.of());

		// when
		GuardianView view = service.update(MARIA_ID, input("Maria Souza", null, LocalDate.of(2026, 3, 1)));

		// then
		assertThat(view.guardian().getBirthDate()).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	private void givenPage(List<Guardian> guardians) {
		when(guardianRepository.findAll(ArgumentMatchers.<Specification<Guardian>>any(), eq(PAGEABLE)))
			.thenReturn(new PageImpl<>(guardians, PAGEABLE, guardians.size()));
	}

	private Guardian givenStored(Guardian guardian) {
		when(guardianRepository.findById(guardian.getId())).thenReturn(Optional.of(guardian));
		return guardian;
	}

	private static Guardian guardian(String name, UUID id) {
		return withId(new Guardian(new GuardianDetails(name, null, null, null, null, "11912345678", false,
				"Padaria Central", "Rua das Flores, 100", "1133334444")), id);
	}

	private static GuardianInput input(String name, String cpf, LocalDate birthDate) {
		return new GuardianInput(name, null, birthDate, cpf, null, "11912345678", false, "Padaria Central",
				"Rua das Flores, 100", "1133334444");
	}

}
