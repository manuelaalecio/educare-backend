package com.manuelaalecio.educare_backend.child.application;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.BIRTH_CERTIFICATE_NUMBER;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.domain.BirthCertificateAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.BirthDateInFutureException;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildNotFoundException;
import com.manuelaalecio.educare_backend.child.domain.ChildRepository;
import com.manuelaalecio.educare_backend.child.domain.CpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.RgAlreadyInUseException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class ChildServiceTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
	private static final Pageable PAGEABLE = PageRequest.of(0, 20);

	private final ChildRepository childRepository = Mockito.mock(ChildRepository.class);

	private ChildService serviceAt(Instant instant) {
		return new ChildService(childRepository, Clock.fixed(instant, ZoneOffset.UTC));
	}

	private final ChildService childService = serviceAt(NOW);

	// ---------- create ----------

	@Test
	void shouldSaveNormalizedChildWhenDataIsValid() {
		// given
		when(childRepository.saveAndFlush(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		Child created = childService.create(aChild().cpf("529.982.247-25")
			.rg("12.345.678-x")
			.birthCertificateNumber("104539.01.55.2013.1.00012.021.0000123-21")
			.build());

		// then
		assertThat(created.getCpf()).isEqualTo("52998224725");
		assertThat(created.getRg()).isEqualTo("12345678X");
		assertThat(created.getBirthCertificateNumber()).isEqualTo(BIRTH_CERTIFICATE_NUMBER);
		verify(childRepository).existsByCpf("52998224725");
		verify(childRepository).existsByRg("12345678X");
		verify(childRepository).existsByBirthCertificateNumber(BIRTH_CERTIFICATE_NUMBER);
	}

	@Test
	void shouldNotCheckDocumentsWhenTheyAreMissing() {
		// given
		when(childRepository.saveAndFlush(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		childService.create(aChild().build());

		// then
		verify(childRepository).saveAndFlush(any(Child.class));
		verifyNoMoreInteractions(childRepository);
	}

	@Test
	void shouldAcceptBirthDateWhenItIsToday() {
		// given
		when(childRepository.saveAndFlush(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		Child created = childService.create(aChild().birthDate(LocalDate.of(2026, 3, 1)).build());

		// then
		assertThat(created.getBirthDate()).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	@Test
	void shouldRejectBirthDateWhenItIsTomorrow() {
		assertThatThrownBy(() -> childService.create(aChild().birthDate(LocalDate.of(2026, 3, 2)).build()))
			.isInstanceOf(BirthDateInFutureException.class);
		verify(childRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldUseSaoPauloDateWhenUtcIsAlreadyTomorrow() {
		// given: 01:00 UTC on March 2nd is still March 1st in São Paulo
		ChildService service = serviceAt(Instant.parse("2026-03-02T01:00:00Z"));

		// when / then
		assertThatThrownBy(() -> service.create(aChild().birthDate(LocalDate.of(2026, 3, 2)).build()))
			.isInstanceOf(BirthDateInFutureException.class);
		assertThat(service.today()).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	@Test
	void shouldRejectCreationWhenCpfIsInUse() {
		// given
		when(childRepository.existsByCpf("52998224725")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().cpf("529.982.247-25").rg("123").build()))
			.isInstanceOf(CpfAlreadyInUseException.class);
		verify(childRepository, never()).existsByRg(anyString());
		verify(childRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectCreationWhenRgIsInUse() {
		// given
		when(childRepository.existsByRg("12345678X")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().rg("12345678-x").build()))
			.isInstanceOf(RgAlreadyInUseException.class);
		verify(childRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectCreationWhenBirthCertificateNumberIsInUse() {
		// given
		when(childRepository.existsByBirthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)
			.build())).isInstanceOf(BirthCertificateAlreadyInUseException.class);
		verify(childRepository, never()).saveAndFlush(any());
	}

	@ParameterizedTest
	@CsvSource({
			"children_cpf_key, CpfAlreadyInUseException",
			"children_rg_key, RgAlreadyInUseException",
			"children_birth_certificate_number_key, BirthCertificateAlreadyInUseException"})
	void shouldConvertConcurrentDocumentViolationWhenCreating(String constraint, String exception) {
		// given
		when(childRepository.saveAndFlush(any(Child.class))).thenThrow(violation(constraint));

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build()))
			.satisfies(ex -> assertThat(ex.getClass().getSimpleName()).isEqualTo(exception));
	}

	@Test
	void shouldRethrowViolationWhenItIsNotAboutDocuments() {
		// given
		DataIntegrityViolationException other = violation("children_state_check");
		when(childRepository.saveAndFlush(any(Child.class))).thenThrow(other);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build())).isSameAs(other);
	}

	@Test
	void shouldRethrowViolationWhenItHasNoConstraintName() {
		// given
		var other = new DataIntegrityViolationException("not null");
		when(childRepository.saveAndFlush(any(Child.class))).thenThrow(other);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build())).isSameAs(other);
	}

	// ---------- findById ----------

	@Test
	void shouldReturnChildWhenItExists() {
		// given
		Child child = withId(new Child(aChild().build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));

		// when / then
		assertThat(childService.findById(ID)).isSameAs(child);
	}

	@Test
	void shouldThrowNotFoundWhenChildDoesNotExist() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.findById(ID)).isInstanceOf(ChildNotFoundException.class);
	}

	// ---------- list ----------

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void shouldListEveryChildWhenNameIsMissingOrBlank(String name) {
		// given
		Page<Child> page = new PageImpl<>(List.of());
		when(childRepository.findAll(PAGEABLE)).thenReturn(page);

		// when / then
		assertThat(childService.list(name, PAGEABLE)).isSameAs(page);
		verify(childRepository, never()).searchByName(anyString(), any());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"joao | %joao%",
			"'  joao  ' | %joao%",
			"% | %\\%%",
			"a_b | %a\\_b%",
			"a\\b | %a\\\\b%"})
	void shouldSearchWithEscapedPatternWhenNameIsGiven(String name, String pattern) {
		// given
		Page<Child> page = new PageImpl<>(List.of());
		when(childRepository.searchByName(pattern, PAGEABLE)).thenReturn(page);

		// when / then
		assertThat(childService.list(name, PAGEABLE)).isSameAs(page);
	}

	// ---------- update ----------

	@Test
	void shouldReplaceDataWhenUpdateIsValid() {
		// given
		Child child = withId(new Child(aChild().medication("Salbutamol").build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));
		when(childRepository.saveAndFlush(child)).thenReturn(child);

		// when
		Child updated = childService.update(ID, aChild().neighborhood("Consolação").build());

		// then
		assertThat(updated.getNeighborhood()).isEqualTo("Consolação");
		assertThat(updated.getMedication()).isNull();
	}

	@Test
	void shouldKeepOwnDocumentsWhenUpdating() {
		// given
		Child child = withId(new Child(aChild().cpf("52998224725").rg("12345678X")
			.birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)
			.build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));
		when(childRepository.saveAndFlush(child)).thenReturn(child);

		// when
		Child updated = childService.update(ID, aChild().cpf("529.982.247-25").rg("12.345.678-x")
			.birthCertificateNumber("104539.01.55.2013.1.00012.021.0000123.21")
			.build());

		// then
		assertThat(updated.getCpf()).isEqualTo("52998224725");
		verify(childRepository).existsByCpfAndIdNot("52998224725", ID);
		verify(childRepository).existsByRgAndIdNot("12345678X", ID);
		verify(childRepository).existsByBirthCertificateNumberAndIdNot(BIRTH_CERTIFICATE_NUMBER, ID);
	}

	@Test
	void shouldThrowNotFoundWhenUpdatingMissingChild() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().build())).isInstanceOf(ChildNotFoundException.class);
	}

	@Test
	void shouldRejectUpdateWhenBirthDateIsInFuture() {
		// given
		Child child = withId(new Child(aChild().build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().birthDate(LocalDate.of(2027, 1, 1)).build()))
			.isInstanceOf(BirthDateInFutureException.class);
		assertThat(child.getBirthDate()).isEqualTo(LocalDate.of(2018, 5, 10));
	}

	@Test
	void shouldRejectUpdateWithoutChangingChildWhenCpfBelongsToAnotherChild() {
		// given
		Child child = withId(new Child(aChild().cpf("11144477735").build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));
		when(childRepository.existsByCpfAndIdNot("52998224725", ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().cpf("52998224725").build()))
			.isInstanceOf(CpfAlreadyInUseException.class);
		assertThat(child.getCpf()).isEqualTo("11144477735");
		verify(childRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectUpdateWhenRgBelongsToAnotherChild() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.of(withId(new Child(aChild().build()), ID)));
		when(childRepository.existsByRgAndIdNot("12345678X", ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().rg("12.345.678-X").build()))
			.isInstanceOf(RgAlreadyInUseException.class);
	}

	@Test
	void shouldRejectUpdateWhenBirthCertificateNumberBelongsToAnotherChild() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.of(withId(new Child(aChild().build()), ID)));
		when(childRepository.existsByBirthCertificateNumberAndIdNot(BIRTH_CERTIFICATE_NUMBER, ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)
			.build())).isInstanceOf(BirthCertificateAlreadyInUseException.class);
	}

	@Test
	void shouldConvertConcurrentDocumentViolationWhenUpdating() {
		// given
		Child child = withId(new Child(aChild().build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));
		when(childRepository.saveAndFlush(child)).thenThrow(violation("children_rg_key"));

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().rg("123").build()))
			.isInstanceOf(RgAlreadyInUseException.class);
	}

	// ---------- delete ----------

	@Test
	void shouldDeleteChildWhenItExists() {
		// given
		Child child = withId(new Child(aChild().build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));

		// when
		childService.delete(ID);

		// then
		verify(childRepository).delete(child);
	}

	@Test
	void shouldThrowNotFoundWhenDeletingMissingChild() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.delete(ID)).isInstanceOf(ChildNotFoundException.class);
		verify(childRepository, never()).delete(any());
	}

	private static Child withId(Child child, UUID id) {
		ReflectionTestUtils.setField(child, "id", id);
		return child;
	}

	private static DataIntegrityViolationException violation(String constraintName) {
		var cause = new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
				"insert into children", constraintName);
		return new DataIntegrityViolationException("could not execute statement", cause);
	}

}
