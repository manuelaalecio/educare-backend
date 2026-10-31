package com.manuelaalecio.educare_backend.classroom.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.classroom.domain.Classroom;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomHasStudentsException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomNameAlreadyInUseException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomNotFoundException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID OTHER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");
	private static final StudentCounts COUNTS = new StudentCounts(3, 1, 1);

	@Mock
	private ClassroomRepository classroomRepository;

	@Mock
	private ClassroomStudentCounter studentCounter;

	@InjectMocks
	private ClassroomService classroomService;

	// ---------- create ----------

	@Test
	void shouldCreateStrippedClassroomWithCountsWhenNameIsFree() {
		// given
		when(classroomRepository.existsByNormalizedName("Turma 3")).thenReturn(false);
		when(classroomRepository.saveAndFlush(any(Classroom.class)))
				.thenAnswer(invocation -> withId(invocation.getArgument(0), ID));
		when(studentCounter.countFor(ID)).thenReturn(StudentCounts.ZERO);

		// when
		ClassroomOverview created = classroomService.create("  Turma 3 ");

		// then
		assertThat(created.classroom().getName()).isEqualTo("Turma 3");
		assertThat(created.classroom().getId()).isEqualTo(ID);
		assertThat(created.students()).isEqualTo(StudentCounts.ZERO);
	}

	@Test
	void shouldRejectCreationWhenNameIsInUse() {
		// given
		when(classroomRepository.existsByNormalizedName("turma 1")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> classroomService.create("turma 1"))
				.isInstanceOf(ClassroomNameAlreadyInUseException.class);
		verify(classroomRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectCreationWhenConcurrentSaveViolatesNameIndex() {
		// given
		when(classroomRepository.existsByNormalizedName("Turma 1")).thenReturn(false);
		when(classroomRepository.saveAndFlush(any(Classroom.class)))
				.thenThrow(violation(ClassroomRepository.NAME_UNIQUE_CONSTRAINT));

		// when / then
		assertThatThrownBy(() -> classroomService.create("Turma 1"))
				.isInstanceOf(ClassroomNameAlreadyInUseException.class);
	}

	@Test
	void shouldRethrowViolationWhenItIsNotTheNameIndex() {
		// given
		DataIntegrityViolationException otherViolation = violation("classrooms_pkey");
		when(classroomRepository.existsByNormalizedName("Turma 1")).thenReturn(false);
		when(classroomRepository.saveAndFlush(any(Classroom.class))).thenThrow(otherViolation);

		// when / then
		assertThatThrownBy(() -> classroomService.create("Turma 1")).isSameAs(otherViolation);
	}

	@Test
	void shouldRethrowViolationWhenItHasNoConstraintDetails() {
		// given
		var notNullViolation = new DataIntegrityViolationException("null value in column \"created_by\"");
		when(classroomRepository.existsByNormalizedName("Turma 1")).thenReturn(false);
		when(classroomRepository.saveAndFlush(any(Classroom.class))).thenThrow(notNullViolation);

		// when / then
		assertThatThrownBy(() -> classroomService.create("Turma 1")).isSameAs(notNullViolation);
	}

	@Test
	void shouldRejectCreationWithoutQueryingWhenNameIsBlank() {
		// when / then
		assertThatThrownBy(() -> classroomService.create("   ")).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(classroomRepository);
	}

	// ---------- list ----------

	@Test
	void shouldListInRepositoryOrderWithCountsWhenListing() {
		// given
		Classroom turma1 = withId(new Classroom("Turma 1"), ID);
		Classroom turma2 = withId(new Classroom("Turma 2"), OTHER_ID);
		when(classroomRepository.findAllOrderedByName()).thenReturn(List.of(turma1, turma2));
		when(studentCounter.countFor(List.of(ID, OTHER_ID))).thenReturn(Map.of(ID, COUNTS));

		// when
		List<ClassroomOverview> classrooms = classroomService.list();

		// then
		assertThat(classrooms).containsExactly(new ClassroomOverview(turma1, COUNTS),
				new ClassroomOverview(turma2, StudentCounts.ZERO));
		verify(studentCounter, times(1)).countFor(anyCollection());
		verify(studentCounter, never()).countFor(any(UUID.class));
	}

	@Test
	void shouldReturnEmptyListWhenThereAreNoClassrooms() {
		// given
		when(classroomRepository.findAllOrderedByName()).thenReturn(List.of());
		when(studentCounter.countFor(List.<UUID>of())).thenReturn(Map.of());

		// when
		List<ClassroomOverview> classrooms = classroomService.list();

		// then
		assertThat(classrooms).isEmpty();
	}

	// ---------- findById ----------

	@Test
	void shouldReturnClassroomWithCountsWhenItExists() {
		// given
		Classroom classroom = withId(new Classroom("Maternal A"), ID);
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom));
		when(studentCounter.countFor(ID)).thenReturn(COUNTS);

		// when
		ClassroomOverview found = classroomService.findById(ID);

		// then
		assertThat(found).isEqualTo(new ClassroomOverview(classroom, COUNTS));
	}

	@Test
	void shouldThrowNotFoundWhenFindingMissingClassroom() {
		// given
		when(classroomRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> classroomService.findById(ID)).isInstanceOf(ClassroomNotFoundException.class);
	}

	// ---------- rename ----------

	@Test
	void shouldRenameKeepingCountsWhenNameIsFree() {
		// given
		Classroom classroom = withId(new Classroom("Turma 1"), ID);
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom));
		when(classroomRepository.existsByNormalizedNameAndIdNot("Maternal A", ID)).thenReturn(false);
		when(classroomRepository.saveAndFlush(classroom)).thenReturn(classroom);
		when(studentCounter.countFor(ID)).thenReturn(COUNTS);

		// when
		ClassroomOverview renamed = classroomService.rename(ID, " Maternal A ");

		// then
		assertThat(renamed.classroom().getName()).isEqualTo("Maternal A");
		assertThat(renamed.students()).isEqualTo(COUNTS);
	}

	@Test
	void shouldRenameWhenNewNameIsVariationOfOwnName() {
		// given
		Classroom classroom = withId(new Classroom("Turma 1"), ID);
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom));
		when(classroomRepository.existsByNormalizedNameAndIdNot("TURMA 1", ID)).thenReturn(false);
		when(classroomRepository.saveAndFlush(classroom)).thenReturn(classroom);
		when(studentCounter.countFor(ID)).thenReturn(StudentCounts.ZERO);

		// when
		ClassroomOverview renamed = classroomService.rename(ID, "TURMA 1");

		// then
		assertThat(renamed.classroom().getName()).isEqualTo("TURMA 1");
	}

	@Test
	void shouldKeepNameWhenRenamingToNameOfAnotherClassroom() {
		// given
		Classroom classroom = withId(new Classroom("Turma 2"), ID);
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom));
		when(classroomRepository.existsByNormalizedNameAndIdNot("TURMA 1", ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> classroomService.rename(ID, "TURMA 1"))
				.isInstanceOf(ClassroomNameAlreadyInUseException.class);
		assertThat(classroom.getName()).isEqualTo("Turma 2");
		verify(classroomRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectRenameWhenConcurrentSaveViolatesNameIndex() {
		// given
		Classroom classroom = withId(new Classroom("Turma 2"), ID);
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom));
		when(classroomRepository.existsByNormalizedNameAndIdNot("Turma 1", ID)).thenReturn(false);
		when(classroomRepository.saveAndFlush(classroom))
				.thenThrow(violation(ClassroomRepository.NAME_UNIQUE_CONSTRAINT));

		// when / then
		assertThatThrownBy(() -> classroomService.rename(ID, "Turma 1"))
				.isInstanceOf(ClassroomNameAlreadyInUseException.class);
	}

	@Test
	void shouldThrowNotFoundWhenRenamingMissingClassroom() {
		// given
		when(classroomRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> classroomService.rename(ID, "Maternal A"))
				.isInstanceOf(ClassroomNotFoundException.class);
		verify(classroomRepository, never()).saveAndFlush(any());
	}

	// ---------- delete ----------

	@Test
	void shouldDeleteWhenClassroomHasNoStudents() {
		// given
		Classroom classroom = withId(new Classroom("Turma 3"), ID);
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom));
		when(studentCounter.countFor(ID)).thenReturn(StudentCounts.ZERO);

		// when
		classroomService.delete(ID);

		// then
		verify(classroomRepository).delete(classroom);
	}

	@Test
	void shouldRejectDeletionWhenClassroomHasOneStudent() {
		// given
		Classroom classroom = withId(new Classroom("Turma 1"), ID);
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom));
		when(studentCounter.countFor(ID)).thenReturn(new StudentCounts(1, 0, 0));

		// when / then
		assertThatThrownBy(() -> classroomService.delete(ID)).isInstanceOf(ClassroomHasStudentsException.class);
		verify(classroomRepository, never()).delete(any());
	}

	@Test
	void shouldThrowNotFoundWhenDeletingMissingClassroom() {
		// given
		when(classroomRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> classroomService.delete(ID)).isInstanceOf(ClassroomNotFoundException.class);
		verifyNoInteractions(studentCounter);
		verify(classroomRepository, never()).delete(any());
	}

	private static Classroom withId(Classroom classroom, UUID id) {
		ReflectionTestUtils.setField(classroom, "id", id);
		return classroom;
	}

	private static DataIntegrityViolationException violation(String constraintName) {
		var cause = new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
				"insert into classrooms", constraintName);
		return new DataIntegrityViolationException("could not execute statement", cause);
	}

}
