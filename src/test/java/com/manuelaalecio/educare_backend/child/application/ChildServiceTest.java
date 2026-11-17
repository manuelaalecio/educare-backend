package com.manuelaalecio.educare_backend.child.application;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.BIRTH_CERTIFICATE_NUMBER;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.domain.BirthCertificateAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.BirthDateInFutureException;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroom;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroomRepository;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.ChildGuardian;
import com.manuelaalecio.educare_backend.child.domain.ChildGuardianRepository;
import com.manuelaalecio.educare_backend.child.domain.ChildNotFoundException;
import com.manuelaalecio.educare_backend.child.domain.ChildRepository;
import com.manuelaalecio.educare_backend.child.domain.ClassroomNotFoundForChildException;
import com.manuelaalecio.educare_backend.child.domain.CpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.GuardianNotFoundForChildException;
import com.manuelaalecio.educare_backend.child.domain.Relationship;
import com.manuelaalecio.educare_backend.child.domain.RgAlreadyInUseException;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomLookup;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianContact;
import com.manuelaalecio.educare_backend.guardian.application.GuardianInput;
import com.manuelaalecio.educare_backend.guardian.application.GuardianRegistry;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianBirthDateInFutureException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.shared.error.FieldConflictException;
import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class ChildServiceTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID OTHER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");
	private static final UUID TURMA_1 = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final UUID TURMA_2 = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c2");
	private static final ClassroomRef TURMA_1_REF = new ClassroomRef(TURMA_1, "Turma 1");
	private static final ClassroomRef TURMA_2_REF = new ClassroomRef(TURMA_2, "Turma 2");
	/** 13:00 UTC is 10:00 in São Paulo. */
	private static final Instant NOW = Instant.parse("2026-11-01T13:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
	private static final LocalDate OCTOBER_1ST = LocalDate.of(2026, 10, 1);
	private static final Pageable PAGEABLE = PageRequest.of(0, 20);
	private static final UUID MARIA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID JOAO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d2");
	private static final UUID CARLOS_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d3");
	private static final GuardianContact MARIA = new GuardianContact(MARIA_ID, "Maria Souza", "11912345678", false,
			"Padaria Central", "1133334444");
	private static final GuardianContact JOAO = new GuardianContact(JOAO_ID, "João Souza", "11955554444", false,
			"Oficina", "1144445555");
	private static final GuardianContact CARLOS = new GuardianContact(CARLOS_ID, "Carlos Souza", "11966665555", true,
			null, null);
	private static final List<ChildGuardianCommand> GUARDIANS = List
		.of(ChildGuardianCommand.existing(MARIA_ID, Relationship.MOTHER));

	private final ChildRepository childRepository = mock(ChildRepository.class);
	private final ChildClassroomRepository linkRepository = mock(ChildClassroomRepository.class);
	private final ClassroomLookup classroomLookup = mock(ClassroomLookup.class);
	private final ChildGuardianRepository guardianLinkRepository = mock(ChildGuardianRepository.class);
	private final GuardianRegistry guardianRegistry = mock(GuardianRegistry.class);
	private final Map<UUID, GuardianContact> registeredGuardians = new HashMap<>();

	private final ChildService childService = serviceAt(NOW);

	private ChildService serviceAt(Instant instant) {
		return new ChildService(childRepository, linkRepository, classroomLookup, guardianLinkRepository,
				guardianRegistry, Clock.fixed(instant, ZoneOffset.UTC));
	}

	/**
	 * Every guardian in {@link #registeredGuardians} exists; the registry returns the ones asked for.
	 */
	@BeforeEach
	void setUpGuardianRegistry() {
		registeredGuardians.put(MARIA_ID, MARIA);
		when(guardianRegistry.findContacts(anyCollection())).thenAnswer(invocation -> {
			Map<UUID, GuardianContact> found = new HashMap<>();
			for (Object id : invocation.getArgument(0, java.util.Collection.class)) {
				if (registeredGuardians.containsKey(id)) {
					found.put((UUID) id, registeredGuardians.get(id));
				}
			}
			return found;
		});
	}

	// ---------- create ----------

	@Test
	void shouldSaveNormalizedChildAndOpenLinkTodayWhenDataIsValid() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);

		// when
		ChildView created = childService.create(aChild().cpf("529.982.247-25")
			.rg("12.345.678-x")
			.birthCertificateNumber("104539.01.55.2013.1.00012.021.0000123-21")
			.build(), TURMA_1, GUARDIANS);

		// then
		assertThat(created.child().getCpf()).isEqualTo("52998224725");
		assertThat(created.child().getRg()).isEqualTo("12345678X");
		assertThat(created.child().getBirthCertificateNumber()).isEqualTo(BIRTH_CERTIFICATE_NUMBER);
		assertThat(created.classroom()).isEqualTo(TURMA_1_REF);
		ChildClassroom link = savedLink();
		assertThat(link.getChildId()).isEqualTo(ID);
		assertThat(link.getClassroomId()).isEqualTo(TURMA_1);
		assertThat(link.getStartDate()).isEqualTo(TODAY);
		assertThat(link.isActive()).isTrue();
		verify(childRepository).existsByCpf("52998224725");
		verify(childRepository).existsByRg("12345678X");
		verify(childRepository).existsByBirthCertificateNumber(BIRTH_CERTIFICATE_NUMBER);
	}

	@Test
	void shouldOpenLinkWithSaoPauloDateWhenUtcIsAlreadyTomorrow() {
		// given: 01:00 UTC on November 2nd is still November 1st in São Paulo
		ChildService service = serviceAt(Instant.parse("2026-11-02T01:00:00Z"));
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);

		// when
		service.create(aChild().build(), TURMA_1, GUARDIANS);

		// then
		assertThat(savedLink().getStartDate()).isEqualTo(TODAY);
	}

	@Test
	void shouldSaveNothingWhenClassroomDoesNotExist() {
		// given
		when(classroomLookup.find(TURMA_1)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1, GUARDIANS))
			.isInstanceOf(ClassroomNotFoundForChildException.class);
		verify(childRepository, never()).saveAndFlush(any());
		verifyNoInteractions(linkRepository);
	}

	@Test
	void shouldNotCheckDocumentsWhenTheyAreMissing() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);

		// when
		childService.create(aChild().build(), TURMA_1, GUARDIANS);

		// then
		verify(childRepository, never()).existsByCpf(any());
		verify(childRepository, never()).existsByRg(any());
		verify(childRepository, never()).existsByBirthCertificateNumber(any());
	}

	@Test
	void shouldAcceptBirthDateWhenItIsToday() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);

		// when
		ChildView created = childService.create(aChild().birthDate(TODAY).build(), TURMA_1, GUARDIANS);

		// then
		assertThat(created.child().getBirthDate()).isEqualTo(TODAY);
	}

	@Test
	void shouldRejectBirthDateWhenItIsTomorrow() {
		assertThatThrownBy(() -> childService.create(aChild().birthDate(TODAY.plusDays(1)).build(), TURMA_1, GUARDIANS))
			.isInstanceOf(BirthDateInFutureException.class);
		verify(childRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldUseSaoPauloDateForBirthDateWhenUtcIsAlreadyTomorrow() {
		// given
		ChildService service = serviceAt(Instant.parse("2026-11-02T01:00:00Z"));

		// when / then
		assertThatThrownBy(() -> service.create(aChild().birthDate(LocalDate.of(2026, 11, 2)).build(), TURMA_1, GUARDIANS))
			.isInstanceOf(BirthDateInFutureException.class);
		assertThat(service.today()).isEqualTo(TODAY);
	}

	@Test
	void shouldRejectCreationWhenCpfIsInUse() {
		// given
		givenClassroom(TURMA_1_REF);
		when(childRepository.existsByCpf("52998224725")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().cpf("529.982.247-25").rg("123").build(), TURMA_1, GUARDIANS))
			.isInstanceOf(CpfAlreadyInUseException.class);
		verify(childRepository, never()).existsByRg(anyString());
		verify(childRepository, never()).saveAndFlush(any());
		verifyNoInteractions(linkRepository);
	}

	@Test
	void shouldRejectCreationWhenRgIsInUse() {
		// given
		givenClassroom(TURMA_1_REF);
		when(childRepository.existsByRg("12345678X")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().rg("12345678-x").build(), TURMA_1, GUARDIANS))
			.isInstanceOf(RgAlreadyInUseException.class);
		verify(childRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectCreationWhenBirthCertificateNumberIsInUse() {
		// given
		givenClassroom(TURMA_1_REF);
		when(childRepository.existsByBirthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)
			.build(), TURMA_1, GUARDIANS)).isInstanceOf(BirthCertificateAlreadyInUseException.class);
		verify(childRepository, never()).saveAndFlush(any());
	}

	@ParameterizedTest
	@CsvSource({
			"children_cpf_key, CpfAlreadyInUseException",
			"children_rg_key, RgAlreadyInUseException",
			"children_birth_certificate_number_key, BirthCertificateAlreadyInUseException"})
	void shouldConvertConcurrentDocumentViolationWhenCreating(String constraint, String exception) {
		// given
		givenClassroom(TURMA_1_REF);
		when(childRepository.saveAndFlush(any(Child.class))).thenThrow(violation(constraint));

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1, GUARDIANS))
			.satisfies(ex -> assertThat(ex.getClass().getSimpleName()).isEqualTo(exception));
	}

	@Test
	void shouldRethrowViolationWhenItIsNotAboutDocuments() {
		// given
		givenClassroom(TURMA_1_REF);
		DataIntegrityViolationException other = violation("children_state_check");
		when(childRepository.saveAndFlush(any(Child.class))).thenThrow(other);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1, GUARDIANS)).isSameAs(other);
	}

	@Test
	void shouldRethrowViolationWhenItHasNoConstraintName() {
		// given
		givenClassroom(TURMA_1_REF);
		var other = new DataIntegrityViolationException("not null");
		when(childRepository.saveAndFlush(any(Child.class))).thenThrow(other);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1, GUARDIANS)).isSameAs(other);
	}

	@Test
	void shouldRejectCreationAsMissingClassroomWhenClassroomIsDeletedMeanwhile() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);
		when(linkRepository.saveAndFlush(any(ChildClassroom.class)))
			.thenThrow(violation("child_classrooms_classroom_id_fkey"));

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1, GUARDIANS))
			.isInstanceOf(ClassroomNotFoundForChildException.class);
	}

	@Test
	void shouldRethrowLinkViolationWhenItIsNotTheClassroomForeignKey() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);
		DataIntegrityViolationException other = violation("child_classrooms_active_key");
		when(linkRepository.saveAndFlush(any(ChildClassroom.class))).thenThrow(other);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1, GUARDIANS)).isSameAs(other);
	}

	// ---------- findById ----------

	@Test
	void shouldReturnChildWithCurrentClassroomWhenItExists() {
		// given
		Child child = withId(new Child(aChild().build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));
		when(linkRepository.findByChildIdAndEndDateIsNull(ID))
			.thenReturn(Optional.of(ChildClassroom.open(ID, TURMA_1, OCTOBER_1ST)));
		givenClassroom(TURMA_1_REF);

		// when
		ChildView view = childService.findById(ID);

		// then
		assertThat(view.child()).isSameAs(child);
		assertThat(view.classroom()).isEqualTo(TURMA_1_REF);
	}

	@Test
	void shouldThrowNotFoundWhenChildDoesNotExist() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.findById(ID)).isInstanceOf(ChildNotFoundException.class);
	}

	@Test
	void shouldFailLoudlyWhenChildHasNoActiveLink() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.of(withId(new Child(aChild().build()), ID)));
		when(linkRepository.findByChildIdAndEndDateIsNull(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.findById(ID)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void shouldFailLoudlyWhenLinkedClassroomIsMissing() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.of(withId(new Child(aChild().build()), ID)));
		when(linkRepository.findByChildIdAndEndDateIsNull(ID))
			.thenReturn(Optional.of(ChildClassroom.open(ID, TURMA_1, OCTOBER_1ST)));
		when(classroomLookup.find(TURMA_1)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.findById(ID)).isInstanceOf(IllegalStateException.class);
	}

	// ---------- list ----------

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void shouldReturnEmptyPageWithoutLookingUpClassroomsWhenNothingMatches(String name) {
		// given
		when(childRepository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty(PAGEABLE));

		// when
		Page<ChildSummaryView> page = childService.list(name, null, PAGEABLE);

		// then
		assertThat(page.getContent()).isEmpty();
		verifyNoInteractions(linkRepository, classroomLookup);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"joao | %joao%",
			"'  joao  ' | %joao%",
			"% | %\\%%",
			"a_b | %a\\_b%",
			"a\\b | %a\\\\b%"})
	void shouldEscapeSearchPatternWhenNameIsGiven(String name, String pattern) {
		assertThat(ChildService.likePattern(name)).isEqualTo(pattern);
	}

	@Test
	void shouldResolveCurrentClassroomsInBatchWhenListing() {
		// given
		Child ana = withId(new Child(aChild().name("Ana").build()), ID);
		Child bruno = withId(new Child(aChild().name("Bruno").build()), OTHER_ID);
		when(childRepository.findAll(any(Specification.class), eq(PAGEABLE)))
			.thenReturn(new PageImpl<>(List.of(ana, bruno), PAGEABLE, 2));
		when(linkRepository.findActiveByChildIdIn(List.of(ID, OTHER_ID)))
			.thenReturn(List.of(ChildClassroom.open(ID, TURMA_1, OCTOBER_1ST),
					ChildClassroom.open(OTHER_ID, TURMA_2, OCTOBER_1ST)));
		when(classroomLookup.findAll(anyCollection()))
			.thenReturn(Map.of(TURMA_1, TURMA_1_REF, TURMA_2, TURMA_2_REF));

		// when
		Page<ChildSummaryView> page = childService.list("ana", TURMA_1, PAGEABLE);

		// then
		assertThat(page.getContent()).extracting(ChildSummaryView::classroom).containsExactly(TURMA_1_REF,
				TURMA_2_REF);
		verify(linkRepository, times(1)).findActiveByChildIdIn(any());
		verify(classroomLookup, times(1)).findAll(anyCollection());
	}

	@Test
	void shouldFailLoudlyWhenListedChildHasNoActiveLink() {
		// given
		when(childRepository.findAll(any(Specification.class), eq(PAGEABLE)))
			.thenReturn(new PageImpl<>(List.of(withId(new Child(aChild().build()), ID)), PAGEABLE, 1));
		when(linkRepository.findActiveByChildIdIn(List.of(ID))).thenReturn(List.of());
		when(classroomLookup.findAll(anyCollection())).thenReturn(Map.of());

		// when / then
		assertThatThrownBy(() -> childService.list(null, null, PAGEABLE)).isInstanceOf(IllegalStateException.class);
	}

	// ---------- update ----------

	@Test
	void shouldReplaceDataWithoutTouchingLinkWhenClassroomIsTheSame() {
		// given
		Child child = givenStoredChild(aChild().medication("Salbutamol").build());
		ChildClassroom current = givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_1_REF);
		when(childRepository.saveAndFlush(child)).thenReturn(child);

		// when
		ChildView updated = childService.update(ID, aChild().neighborhood("Consolação").build(), TURMA_1, GUARDIANS);

		// then
		assertThat(updated.child().getNeighborhood()).isEqualTo("Consolação");
		assertThat(updated.child().getMedication()).isNull();
		assertThat(updated.classroom()).isEqualTo(TURMA_1_REF);
		assertThat(current.isActive()).isTrue();
		verify(linkRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldCloseCurrentLinkBeforeOpeningNewOneWhenClassroomChanges() {
		// given
		Child child = givenStoredChild(aChild().build());
		ChildClassroom current = givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_2_REF);
		when(childRepository.saveAndFlush(child)).thenReturn(child);

		// when
		ChildView updated = childService.update(ID, aChild().build(), TURMA_2, GUARDIANS);

		// then
		assertThat(updated.classroom()).isEqualTo(TURMA_2_REF);
		assertThat(current.getEndDate()).isEqualTo(TODAY);
		ArgumentCaptor<ChildClassroom> saved = ArgumentCaptor.forClass(ChildClassroom.class);
		InOrder order = inOrder(linkRepository);
		order.verify(linkRepository, times(2)).saveAndFlush(saved.capture());
		assertThat(saved.getAllValues().get(0)).isSameAs(current);
		ChildClassroom opened = saved.getAllValues().get(1);
		assertThat(opened.getClassroomId()).isEqualTo(TURMA_2);
		assertThat(opened.getStartDate()).isEqualTo(TODAY);
		assertThat(opened.isActive()).isTrue();
	}

	@Test
	void shouldLockChildWhenUpdating() {
		// given
		Child child = givenStoredChild(aChild().build());
		givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_1_REF);
		when(childRepository.saveAndFlush(child)).thenReturn(child);

		// when
		childService.update(ID, aChild().build(), TURMA_1, GUARDIANS);

		// then
		verify(childRepository).findByIdForUpdate(ID);
		verify(childRepository, never()).findById(any());
	}

	@Test
	void shouldChangeNothingWhenNewClassroomDoesNotExist() {
		// given
		Child child = givenStoredChild(aChild().build());
		when(classroomLookup.find(TURMA_2)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().name("Outro nome").build(), TURMA_2, GUARDIANS))
			.isInstanceOf(ClassroomNotFoundForChildException.class);
		assertThat(child.getName()).isEqualTo("Maria Clara Souza");
		verify(childRepository, never()).saveAndFlush(any());
		verifyNoInteractions(linkRepository);
	}

	@Test
	void shouldChangeNeitherChildNorLinkWhenCpfBelongsToAnotherChild() {
		// given
		Child child = givenStoredChild(aChild().cpf("11144477735").build());
		givenClassroom(TURMA_2_REF);
		when(childRepository.existsByCpfAndIdNot("52998224725", ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().cpf("52998224725").build(), TURMA_2, GUARDIANS))
			.isInstanceOf(CpfAlreadyInUseException.class);
		assertThat(child.getCpf()).isEqualTo("11144477735");
		verify(childRepository, never()).saveAndFlush(any());
		verifyNoInteractions(linkRepository);
	}

	@Test
	void shouldKeepOwnDocumentsWhenUpdating() {
		// given
		Child child = givenStoredChild(aChild().cpf("52998224725").rg("12345678X")
			.birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)
			.build());
		givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_1_REF);
		when(childRepository.saveAndFlush(child)).thenReturn(child);

		// when
		childService.update(ID, aChild().cpf("529.982.247-25").rg("12.345.678-x")
			.birthCertificateNumber("104539.01.55.2013.1.00012.021.0000123.21")
			.build(), TURMA_1, GUARDIANS);

		// then
		verify(childRepository).existsByCpfAndIdNot("52998224725", ID);
		verify(childRepository).existsByRgAndIdNot("12345678X", ID);
		verify(childRepository).existsByBirthCertificateNumberAndIdNot(BIRTH_CERTIFICATE_NUMBER, ID);
	}

	@Test
	void shouldThrowNotFoundWhenUpdatingMissingChild() {
		// given
		when(childRepository.findByIdForUpdate(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().build(), TURMA_1, GUARDIANS))
			.isInstanceOf(ChildNotFoundException.class);
	}

	@Test
	void shouldRejectUpdateWhenBirthDateIsInFuture() {
		// given
		Child child = givenStoredChild(aChild().build());

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().birthDate(LocalDate.of(2027, 1, 1)).build(),
				TURMA_1, GUARDIANS)).isInstanceOf(BirthDateInFutureException.class);
		assertThat(child.getBirthDate()).isEqualTo(LocalDate.of(2018, 5, 10));
	}

	@Test
	void shouldRejectUpdateWhenRgBelongsToAnotherChild() {
		// given
		givenStoredChild(aChild().build());
		givenClassroom(TURMA_1_REF);
		when(childRepository.existsByRgAndIdNot("12345678X", ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().rg("12.345.678-X").build(), TURMA_1, GUARDIANS))
			.isInstanceOf(RgAlreadyInUseException.class);
	}

	@Test
	void shouldRejectUpdateWhenBirthCertificateNumberBelongsToAnotherChild() {
		// given
		givenStoredChild(aChild().build());
		givenClassroom(TURMA_1_REF);
		when(childRepository.existsByBirthCertificateNumberAndIdNot(BIRTH_CERTIFICATE_NUMBER, ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)
			.build(), TURMA_1, GUARDIANS)).isInstanceOf(BirthCertificateAlreadyInUseException.class);
	}

	@Test
	void shouldConvertConcurrentDocumentViolationWhenUpdating() {
		// given
		Child child = givenStoredChild(aChild().build());
		givenClassroom(TURMA_1_REF);
		when(childRepository.saveAndFlush(child)).thenThrow(violation("children_rg_key"));

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().rg("123").build(), TURMA_1, GUARDIANS))
			.isInstanceOf(RgAlreadyInUseException.class);
	}

	@Test
	void shouldRejectUpdateAsMissingClassroomWhenNewClassroomIsDeletedMeanwhile() {
		// given
		Child child = givenStoredChild(aChild().build());
		ChildClassroom current = givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_2_REF);
		when(childRepository.saveAndFlush(child)).thenReturn(child);
		when(linkRepository.saveAndFlush(any(ChildClassroom.class))).thenAnswer(invocation -> {
			if (invocation.getArgument(0) == current) {
				return current;
			}
			throw violation("child_classrooms_classroom_id_fkey");
		});

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().build(), TURMA_2, GUARDIANS))
			.isInstanceOf(ClassroomNotFoundForChildException.class);
	}

	// ---------- history ----------

	@Test
	void shouldReturnLinksWithClassroomNamesWhenReadingHistory() {
		// given
		ChildClassroom old = ChildClassroom.open(ID, TURMA_1, OCTOBER_1ST);
		old.close(TODAY);
		ChildClassroom current = ChildClassroom.open(ID, TURMA_2, TODAY);
		when(childRepository.existsById(ID)).thenReturn(true);
		when(linkRepository.findHistory(ID)).thenReturn(List.of(current, old));
		when(classroomLookup.findAll(List.of(TURMA_2, TURMA_1)))
			.thenReturn(Map.of(TURMA_1, TURMA_1_REF, TURMA_2, TURMA_2_REF));

		// when
		List<ChildClassroomView> history = childService.history(ID);

		// then
		assertThat(history).extracting(ChildClassroomView::classroom).containsExactly(TURMA_2_REF, TURMA_1_REF);
		assertThat(history).extracting(ChildClassroomView::link).containsExactly(current, old);
	}

	@Test
	void shouldThrowNotFoundWhenReadingHistoryOfMissingChild() {
		// given
		when(childRepository.existsById(ID)).thenReturn(false);

		// when / then
		assertThatThrownBy(() -> childService.history(ID)).isInstanceOf(ChildNotFoundException.class);
		verifyNoInteractions(linkRepository);
	}

	// ---------- delete ----------

	@Test
	void shouldDeleteChildWithoutLoadingLinksWhenItExists() {
		// given
		Child child = withId(new Child(aChild().build()), ID);
		when(childRepository.findById(ID)).thenReturn(Optional.of(child));

		// when
		childService.delete(ID);

		// then
		verify(childRepository).delete(child);
		verifyNoInteractions(linkRepository, guardianLinkRepository);
	}

	@Test
	void shouldThrowNotFoundWhenDeletingMissingChild() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> childService.delete(ID)).isInstanceOf(ChildNotFoundException.class);
		verify(childRepository, never()).delete(any(Child.class));
	}

	// ---------- guardians ----------

	@Test
	void shouldLinkExistingGuardianAndReturnItWhenCreating() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);

		// when
		ChildView created = childService.create(aChild().build(), TURMA_1, GUARDIANS);

		// then
		assertThat(savedGuardianLinks()).singleElement().satisfies(link -> {
			assertThat(link.getChildId()).isEqualTo(ID);
			assertThat(link.getGuardianId()).isEqualTo(MARIA_ID);
			assertThat(link.getRelationship()).isEqualTo(Relationship.MOTHER);
		});
		assertThat(created.guardians()).containsExactly(new ChildGuardianView(MARIA, Relationship.MOTHER));
		verify(guardianRegistry, never()).create(any());
	}

	@Test
	void shouldCreateNewGuardianAndLinkItWhenCreating() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);
		GuardianInput joao = newGuardian("João Souza");
		when(guardianRegistry.create(joao)).thenAnswer(invocation -> {
			registeredGuardians.put(JOAO_ID, JOAO);
			return JOAO_ID;
		});

		// when
		ChildView created = childService.create(aChild().build(), TURMA_1,
				List.of(ChildGuardianCommand.existing(MARIA_ID, Relationship.MOTHER),
						ChildGuardianCommand.newGuardian(joao, Relationship.FATHER)));

		// then
		assertThat(savedGuardianLinks()).extracting(ChildGuardian::getGuardianId, ChildGuardian::getRelationship)
			.containsExactly(tuple(MARIA_ID, Relationship.MOTHER), tuple(JOAO_ID, Relationship.FATHER));
		assertThat(created.guardians()).containsExactly(new ChildGuardianView(JOAO, Relationship.FATHER),
				new ChildGuardianView(MARIA, Relationship.MOTHER));
	}

	@Test
	void shouldPointToItemWithoutSavingWhenExistingGuardianDoesNotExist() {
		// given
		givenClassroom(TURMA_1_REF);
		UUID missing = UUID.fromString("0190f4a2-0000-7000-8000-000000000000");

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1,
				List.of(ChildGuardianCommand.newGuardian(newGuardian("João Souza"), Relationship.FATHER),
						ChildGuardianCommand.existing(missing, Relationship.MOTHER))))
			.isInstanceOfSatisfying(GuardianNotFoundForChildException.class,
					ex -> assertThat(ex.getField()).isEqualTo("guardians[1].guardianId"));
		verify(guardianRegistry, never()).create(any());
		verify(childRepository, never()).saveAndFlush(any());
		verifyNoInteractions(guardianLinkRepository);
	}

	@Test
	void shouldRethrowNewGuardianDateErrorOnItemWhenCreating() {
		// given
		givenClassroom(TURMA_1_REF);
		when(guardianRegistry.create(any())).thenThrow(new GuardianBirthDateInFutureException());

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1,
				List.of(ChildGuardianCommand.existing(MARIA_ID, Relationship.MOTHER),
						ChildGuardianCommand.newGuardian(newGuardian("João Souza"), Relationship.FATHER))))
			.isInstanceOfSatisfying(InvalidFieldException.class, ex -> {
				assertThat(ex.getField()).isEqualTo("guardians[1].guardian.birthDate");
				assertThat(ex).hasMessage("Data de nascimento não pode ser futura");
			});
		verify(childRepository, never()).saveAndFlush(any());
		verifyNoInteractions(guardianLinkRepository);
	}

	@Test
	void shouldRethrowNewGuardianCpfConflictOnItemWhenCreating() {
		// given
		givenClassroom(TURMA_1_REF);
		when(guardianRegistry.create(any())).thenThrow(new GuardianCpfAlreadyInUseException());

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().build(), TURMA_1,
				List.of(ChildGuardianCommand.newGuardian(newGuardian("João Souza"), Relationship.FATHER))))
			.isInstanceOfSatisfying(FieldConflictException.class, ex -> {
				assertThat(ex.getField()).isEqualTo("guardians[0].guardian.cpf");
				assertThat(ex).hasMessage("Já existe um responsável com este CPF.");
			});
		verify(childRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRefuseChildCpfBeforeCreatingAnyGuardianWhenCreating() {
		// given
		givenClassroom(TURMA_1_REF);
		when(childRepository.existsByCpf("52998224725")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.create(aChild().cpf("52998224725").build(), TURMA_1,
				List.of(ChildGuardianCommand.newGuardian(newGuardian("João Souza"), Relationship.FATHER))))
			.isInstanceOf(CpfAlreadyInUseException.class);
		verifyNoInteractions(guardianRegistry);
	}

	@Test
	void shouldOrderGuardiansByNameAndThenIdWhenReturningChild() {
		// given
		givenClassroom(TURMA_1_REF);
		givenChildSavedWithId(ID);
		UUID otherMariaId = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d0");
		GuardianContact otherMaria = new GuardianContact(otherMariaId, "Maria Souza", "11900000000", true, null, null);
		GuardianContact angela = new GuardianContact(JOAO_ID, "Ângela Souza", "11900000001", true, null, null);
		registeredGuardians.put(otherMariaId, otherMaria);
		registeredGuardians.put(JOAO_ID, angela);
		registeredGuardians.put(CARLOS_ID, CARLOS);

		// when
		ChildView created = childService.create(aChild().build(), TURMA_1,
				List.of(ChildGuardianCommand.existing(MARIA_ID, Relationship.MOTHER),
						ChildGuardianCommand.existing(CARLOS_ID, Relationship.GRANDFATHER),
						ChildGuardianCommand.existing(otherMariaId, Relationship.AUNT),
						ChildGuardianCommand.existing(JOAO_ID, Relationship.GRANDMOTHER)));

		// then
		assertThat(created.guardians()).extracting(view -> view.contact().id())
			.containsExactly(JOAO_ID, CARLOS_ID, otherMariaId, MARIA_ID);
	}

	@Test
	void shouldReplaceGuardianLinksWhenUpdating() {
		// given
		givenStoredChild(aChild().build());
		givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_1_REF);
		when(childRepository.saveAndFlush(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));
		registeredGuardians.put(CARLOS_ID, CARLOS);
		ChildGuardian mariaLink = ChildGuardian.link(ID, MARIA_ID, Relationship.OTHER);
		ChildGuardian carlosLink = ChildGuardian.link(ID, CARLOS_ID, Relationship.GRANDFATHER);
		when(guardianLinkRepository.findByChildId(ID)).thenReturn(List.of(mariaLink, carlosLink));
		GuardianInput joao = newGuardian("João Souza");
		when(guardianRegistry.create(joao)).thenAnswer(invocation -> {
			registeredGuardians.put(JOAO_ID, JOAO);
			return JOAO_ID;
		});

		// when
		ChildView updated = childService.update(ID, aChild().build(), TURMA_1,
				List.of(ChildGuardianCommand.existing(MARIA_ID, Relationship.STEPMOTHER),
						ChildGuardianCommand.newGuardian(joao, Relationship.FATHER)));

		// then
		InOrder order = inOrder(guardianLinkRepository);
		order.verify(guardianLinkRepository).deleteAllInBatch(List.of(carlosLink));
		order.verify(guardianLinkRepository).saveAllAndFlush(any());
		assertThat(mariaLink.getRelationship()).isEqualTo(Relationship.STEPMOTHER);
		assertThat(savedGuardianLinks()).extracting(ChildGuardian::getGuardianId, ChildGuardian::getRelationship)
			.containsExactly(tuple(MARIA_ID, Relationship.STEPMOTHER), tuple(JOAO_ID, Relationship.FATHER));
		assertThat(savedGuardianLinks().get(0)).isSameAs(mariaLink);
		assertThat(updated.guardians()).containsExactly(new ChildGuardianView(JOAO, Relationship.FATHER),
				new ChildGuardianView(MARIA, Relationship.STEPMOTHER));
	}

	@Test
	void shouldNotDeleteAnyLinkWhenEveryGuardianStays() {
		// given
		givenStoredChild(aChild().build());
		givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_1_REF);
		when(childRepository.saveAndFlush(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ChildGuardian mariaLink = ChildGuardian.link(ID, MARIA_ID, Relationship.MOTHER);
		when(guardianLinkRepository.findByChildId(ID)).thenReturn(List.of(mariaLink));

		// when
		childService.update(ID, aChild().build(), TURMA_1, GUARDIANS);

		// then
		verify(guardianLinkRepository, never()).deleteAllInBatch(any());
		assertThat(savedGuardianLinks()).containsExactly(mariaLink);
		assertThat(mariaLink.getRelationship()).isEqualTo(Relationship.MOTHER);
	}

	@Test
	void shouldNotTouchGuardiansWhenUpdateIsRefused() {
		// given
		givenStoredChild(aChild().build());
		givenClassroom(TURMA_1_REF);
		when(childRepository.existsByCpfAndIdNot("11144477735", ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> childService.update(ID, aChild().cpf("11144477735").build(), TURMA_1,
				List.of(ChildGuardianCommand.newGuardian(newGuardian("João Souza"), Relationship.FATHER))))
			.isInstanceOf(CpfAlreadyInUseException.class);
		verifyNoInteractions(guardianRegistry, guardianLinkRepository);
	}

	@Test
	void shouldReturnEmptyGuardiansWhenChildHasNoLinks() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.of(withId(new Child(aChild().build()), ID)));
		givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_1_REF);
		when(guardianLinkRepository.findByChildId(ID)).thenReturn(List.of());

		// when / then
		assertThat(childService.findById(ID).guardians()).isEmpty();
	}

	@Test
	void shouldReturnGuardiansByNameWithRelationshipWhenFindingChild() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.of(withId(new Child(aChild().build()), ID)));
		givenActiveLink(TURMA_1, OCTOBER_1ST);
		givenClassroom(TURMA_1_REF);
		registeredGuardians.put(CARLOS_ID, CARLOS);
		when(guardianLinkRepository.findByChildId(ID)).thenReturn(List.of(
				ChildGuardian.link(ID, MARIA_ID, Relationship.MOTHER),
				ChildGuardian.link(ID, CARLOS_ID, Relationship.GRANDFATHER)));

		// when
		ChildView view = childService.findById(ID);

		// then
		assertThat(view.guardians()).containsExactly(new ChildGuardianView(CARLOS, Relationship.GRANDFATHER),
				new ChildGuardianView(MARIA, Relationship.MOTHER));
		verify(guardianRegistry, times(1)).findContacts(anyCollection());
	}

	@Test
	void shouldFailLoudlyWhenLinkedGuardianIsMissing() {
		// given
		when(childRepository.findById(ID)).thenReturn(Optional.of(withId(new Child(aChild().build()), ID)));
		when(guardianLinkRepository.findByChildId(ID))
			.thenReturn(List.of(ChildGuardian.link(ID, CARLOS_ID, Relationship.GRANDFATHER)));

		// when / then
		assertThatThrownBy(() -> childService.findById(ID)).isInstanceOf(IllegalStateException.class);
	}

	// ---------- helpers ----------

	private void givenClassroom(ClassroomRef classroom) {
		when(classroomLookup.find(classroom.id())).thenReturn(Optional.of(classroom));
	}

	private void givenChildSavedWithId(UUID id) {
		when(childRepository.saveAndFlush(any(Child.class)))
			.thenAnswer(invocation -> withId(invocation.getArgument(0), id));
	}

	private Child givenStoredChild(ChildDetails details) {
		Child child = withId(new Child(details), ID);
		when(childRepository.findByIdForUpdate(ID)).thenReturn(Optional.of(child));
		return child;
	}

	private ChildClassroom givenActiveLink(UUID classroomId, LocalDate startDate) {
		ChildClassroom link = ChildClassroom.open(ID, classroomId, startDate);
		when(linkRepository.findByChildIdAndEndDateIsNull(ID)).thenReturn(Optional.of(link));
		return link;
	}

	private ChildClassroom savedLink() {
		ArgumentCaptor<ChildClassroom> captor = ArgumentCaptor.forClass(ChildClassroom.class);
		verify(linkRepository).saveAndFlush(captor.capture());
		return captor.getValue();
	}

	private List<ChildGuardian> savedGuardianLinks() {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ChildGuardian>> captor = ArgumentCaptor.forClass(List.class);
		verify(guardianLinkRepository).saveAllAndFlush(captor.capture());
		return captor.getValue();
	}

	private static GuardianInput newGuardian(String name) {
		return new GuardianInput(name, null, null, null, null, "11955554444", false, "Oficina", "Rua B, 2",
				"1144445555");
	}

	private static Child withId(Child child, UUID id) {
		ReflectionTestUtils.setField(child, "id", id);
		return child;
	}

	private static DataIntegrityViolationException violation(String constraintName) {
		var cause = new ConstraintViolationException("violation", new SQLException("violation"), "statement",
				constraintName);
		return new DataIntegrityViolationException("could not execute statement", cause);
	}

}
