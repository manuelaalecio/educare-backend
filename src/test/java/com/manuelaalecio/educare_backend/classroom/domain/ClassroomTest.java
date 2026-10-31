package com.manuelaalecio.educare_backend.classroom.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClassroomTest {

	@Test
	void shouldStripNameWhenCreated() {
		// when
		var classroom = new Classroom("  Turma 3 ");

		// then
		assertThat(classroom.getName()).isEqualTo("Turma 3");
	}

	@Test
	void shouldStripNameWhenRenamed() {
		// given
		var classroom = new Classroom("Turma 1");

		// when
		classroom.rename(" Maternal A  ");

		// then
		assertThat(classroom.getName()).isEqualTo("Maternal A");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t"})
	void shouldRejectNameWhenBlank(String name) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Classroom(name))
				.withMessage("Nome da turma é obrigatório");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void shouldKeepNameWhenRenamedToBlank(String name) {
		// given
		var classroom = new Classroom("Turma 1");

		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> classroom.rename(name));
		assertThat(classroom.getName()).isEqualTo("Turma 1");
	}

	@Test
	void shouldAcceptNameWhenItHasMaxLengthAfterStripping() {
		// when
		var classroom = new Classroom(" " + "a".repeat(60) + " ");

		// then
		assertThat(classroom.getName()).hasSize(60);
	}

	@Test
	void shouldRejectNameWhenLongerThanMaxLength() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Classroom("a".repeat(61)))
				.withMessage("Nome da turma deve ter no máximo 60 caracteres");
	}

	@Test
	void shouldKeepNameWhenRenamedToTooLongName() {
		// given
		var classroom = new Classroom("Turma 1");

		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> classroom.rename("a".repeat(61)));
		assertThat(classroom.getName()).isEqualTo("Turma 1");
	}

	@Test
	void shouldReturnStoredFormWhenNormalizingName() {
		assertThat(Classroom.normalizeName("  Turma 1 ")).isEqualTo("Turma 1");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void shouldRejectBlankNameWhenNormalizing(String name) {
		assertThatIllegalArgumentException().isThrownBy(() -> Classroom.normalizeName(name));
	}

	@Test
	void shouldNotExposeNameWhenExceptionsAreCreated() {
		// when
		String conflict = new ClassroomNameAlreadyInUseException().getMessage();
		String hasStudents = new ClassroomHasStudentsException().getMessage();
		String notFound = new ClassroomNotFoundException().getMessage();

		// then
		assertThat(conflict).isEqualTo("Já existe uma turma com esse nome");
		assertThat(hasStudents).isEqualTo("A turma tem alunos. Tire os alunos da turma antes de excluí-la");
		assertThat(notFound).isEqualTo("Turma não encontrada");
	}

}
