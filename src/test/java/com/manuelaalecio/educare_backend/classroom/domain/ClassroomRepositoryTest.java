package com.manuelaalecio.educare_backend.classroom.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.persistence.JpaAuditingConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.AuthenticatedAs;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every test runs in its own transaction, rolled back at the end, so the tests do not see each other's classes.
 * Classes are saved inside {@link AuthenticatedAs}, because {@code created_by}/{@code updated_by} are NOT NULL.
 */
@DataJpaTest
// the JPA slice does not scan @Configuration classes, so auditing is imported explicitly
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})
class ClassroomRepositoryTest {

	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000a1");
	private static final UUID BRUNO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000b1");

	@Autowired
	private ClassroomRepository classroomRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@ParameterizedTest
	@ValueSource(strings = {"Turma 1", "turma 1", "TÚRMA 1", "Túrma 1"})
	void shouldFindNameWhenItDiffersOnlyInCaseOrAccents(String name) {
		// given
		save("Turma 1");

		// when
		boolean exists = classroomRepository.existsByNormalizedName(name);

		// then
		assertThat(exists).isTrue();
	}

	@Test
	void shouldNotFindNameWhenItIsAnotherName() {
		// given
		save("Turma 1");

		// when
		boolean exists = classroomRepository.existsByNormalizedName("Turma 10");

		// then
		assertThat(exists).isFalse();
	}

	@Test
	void shouldIgnoreOwnClassroomWhenCheckingNameExceptId() {
		// given
		Classroom turma1 = save("Turma 1");

		// when
		boolean exists = classroomRepository.existsByNormalizedNameAndIdNot("TURMA 1", turma1.getId());

		// then
		assertThat(exists).isFalse();
	}

	@Test
	void shouldFindOtherClassroomWhenCheckingNameExceptId() {
		// given
		save("Turma 1");
		Classroom turma2 = save("Turma 2");

		// when
		boolean exists = classroomRepository.existsByNormalizedNameAndIdNot("turma 1", turma2.getId());

		// then
		assertThat(exists).isTrue();
	}

	@Test
	void shouldListAlphabeticallyIgnoringCaseWhenListing() {
		// given
		save("Turma 2");
		save("berçário");
		save("Turma 1");

		// when
		List<Classroom> classrooms = classroomRepository.findAllOrderedByName();

		// then
		assertThat(classrooms).extracting(Classroom::getName).containsExactly("berçário", "Turma 1", "Turma 2");
	}

	@Test
	void shouldListAlphabeticallyIgnoringAccentsWhenListing() {
		// given
		save("Évora");
		save("Educação Infantil");
		save("Ensino");

		// when
		List<Classroom> classrooms = classroomRepository.findAllOrderedByName();

		// then
		assertThat(classrooms).extracting(Classroom::getName)
				.containsExactly("Educação Infantil", "Ensino", "Évora");
	}

	@Test
	void shouldRejectInsertWhenNameDiffersOnlyInCaseAndAccents() {
		// given
		save("Turma 1");

		// when / then
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO classrooms (id, name, created_at, updated_at, created_by, updated_by)
				VALUES (?, 'TÚRMA 1', now(), now(), ?, ?)
				""", UUID.randomUUID(), ANA_ID, ANA_ID))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(ClassroomRepository.NAME_UNIQUE_CONSTRAINT);
	}

	@Test
	void shouldReportNameIndexWhenEntityWithSameNormalizedNameIsSaved() {
		// given
		save("Turma 1");

		// when / then: the service relies on this constraint name to turn the violation into a conflict
		assertThatThrownBy(() -> save("TÚRMA 1"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.cause().isInstanceOfSatisfying(ConstraintViolationException.class, violation ->
						assertThat(violation.getConstraintName())
								.isEqualTo(ClassroomRepository.NAME_UNIQUE_CONSTRAINT));
	}

	@Test
	void shouldRecordAuthorsWhenSavedAndRenamed() {
		// given
		Classroom saved = save("Turma 1");

		// when
		AuthenticatedAs.run(BRUNO_ID, () -> {
			saved.rename("Maternal A");
			classroomRepository.saveAndFlush(saved);
		});

		// then
		assertThat(authorInDatabase("created_by", saved.getId())).isEqualTo(ANA_ID);
		assertThat(authorInDatabase("updated_by", saved.getId())).isEqualTo(BRUNO_ID);
	}

	@Test
	void shouldRejectSaveWhenNoUserIsAuthenticated() {
		// when / then
		assertThatThrownBy(() -> classroomRepository.saveAndFlush(new Classroom("Turma 1")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Classroom save(String name) {
		return AuthenticatedAs.call(ANA_ID, () -> classroomRepository.saveAndFlush(new Classroom(name)));
	}

	private UUID authorInDatabase(String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM classrooms WHERE id = ?", UUID.class, id);
	}

}
