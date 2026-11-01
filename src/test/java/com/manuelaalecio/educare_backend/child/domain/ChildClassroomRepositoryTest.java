package com.manuelaalecio.educare_backend.child.domain;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.child.ChildDetailsBuilder;
import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.persistence.JpaAuditingConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.AuthenticatedAs;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every test runs in its own transaction, rolled back at the end. Children and links are saved inside
 * {@link AuthenticatedAs}, because {@code created_by}/{@code updated_by} are NOT NULL; classes belong to another
 * module, so they are inserted straight into the table.
 */
@DataJpaTest
// the JPA slice does not scan @Configuration classes, so auditing is imported explicitly
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})
class ChildClassroomRepositoryTest {

	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000a1");
	private static final LocalDate OCTOBER_1ST = LocalDate.of(2026, 10, 1);
	private static final LocalDate NOVEMBER_1ST = LocalDate.of(2026, 11, 1);

	@Autowired
	private ChildClassroomRepository linkRepository;

	@Autowired
	private ChildRepository childRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void shouldRejectSecondActiveLinkWhenChildIsAlreadyInClassroom() {
		// given
		UUID child = saveChild(aChild());
		save(ChildClassroom.open(child, insertClassroom("Turma 1"), OCTOBER_1ST));

		// when / then
		assertThatThrownBy(() -> save(ChildClassroom.open(child, insertClassroom("Turma 2"), NOVEMBER_1ST)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("child_classrooms_active_key");
	}

	@Test
	void shouldAcceptManyEndedLinksWhenChildChangedClassrooms() {
		// given
		UUID child = saveChild(aChild());
		UUID turma1 = insertClassroom("Turma 1");
		UUID turma2 = insertClassroom("Turma 2");

		// when
		save(closed(ChildClassroom.open(child, turma1, OCTOBER_1ST), NOVEMBER_1ST));
		save(closed(ChildClassroom.open(child, turma2, NOVEMBER_1ST), NOVEMBER_1ST));
		save(ChildClassroom.open(child, turma1, NOVEMBER_1ST));

		// then
		assertThat(linkRepository.findHistory(child)).hasSize(3);
	}

	@Test
	void shouldRejectEndDateBeforeStartDateWhenInsertedDirectly() {
		// given
		UUID child = saveChild(aChild());
		UUID classroom = insertClassroom("Turma 1");

		// when / then
		assertThatThrownBy(() -> insertLink(child, classroom, NOVEMBER_1ST, OCTOBER_1ST))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("child_classrooms_dates_check");
	}

	@Test
	void shouldDeleteLinksWhenChildIsDeleted() {
		// given
		UUID child = saveChild(aChild());
		UUID classroom = insertClassroom("Turma 1");
		insertLink(child, classroom, OCTOBER_1ST, NOVEMBER_1ST);
		insertLink(child, insertClassroom("Turma 2"), NOVEMBER_1ST, null);

		// when
		jdbcTemplate.update("DELETE FROM children WHERE id = ?", child);

		// then
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM child_classrooms", Integer.class)).isZero();
	}

	@Test
	void shouldRefuseClassroomDeletionWhenItHasLinks() {
		// given
		UUID classroom = insertClassroom("Turma 1");
		insertLink(saveChild(aChild()), classroom, OCTOBER_1ST, NOVEMBER_1ST);

		// when / then: 23503 is the foreign key violation the classroom service relies on
		assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM classrooms WHERE id = ?", classroom))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("child_classrooms_classroom_id_fkey");
	}

	@Test
	void shouldFindActiveLinkOnlyWhenLookingUpCurrentClassroom() {
		// given
		UUID child = saveChild(aChild());
		UUID turma2 = insertClassroom("Turma 2");
		insertLink(child, insertClassroom("Turma 1"), OCTOBER_1ST, NOVEMBER_1ST);
		insertLink(child, turma2, NOVEMBER_1ST, null);

		// when / then
		assertThat(linkRepository.findByChildIdAndEndDateIsNull(child)).get()
			.extracting(ChildClassroom::getClassroomId)
			.isEqualTo(turma2);
	}

	@Test
	void shouldFindActiveLinksOfSeveralChildrenWhenLookingUpPage() {
		// given
		UUID ana = saveChild(aChild().name("Ana"));
		UUID bruno = saveChild(aChild().name("Bruno"));
		UUID turma1 = insertClassroom("Turma 1");
		UUID turma2 = insertClassroom("Turma 2");
		insertLink(ana, turma1, OCTOBER_1ST, NOVEMBER_1ST);
		insertLink(ana, turma2, NOVEMBER_1ST, null);
		insertLink(bruno, turma1, OCTOBER_1ST, null);

		// when
		Map<UUID, UUID> classroomByChild = linkRepository.findActiveByChildIdIn(List.of(ana, bruno))
			.stream()
			.collect(Collectors.toMap(ChildClassroom::getChildId, ChildClassroom::getClassroomId));

		// then
		assertThat(classroomByChild).containsOnly(Map.entry(ana, turma2), Map.entry(bruno, turma1));
	}

	@Test
	void shouldListMostRecentFirstWhenChildChangedTwiceOnSameDay() {
		// given
		UUID child = saveChild(aChild());
		UUID turma1 = insertClassroom("Turma 1");
		UUID turma2 = insertClassroom("Turma 2");
		UUID turma3 = insertClassroom("Turma 3");
		insertLink(child, turma2, NOVEMBER_1ST, NOVEMBER_1ST);
		insertLink(child, turma3, NOVEMBER_1ST, null);
		insertLink(child, turma1, OCTOBER_1ST, NOVEMBER_1ST);

		// when
		List<ChildClassroom> history = linkRepository.findHistory(child);

		// then
		assertThat(history).extracting(ChildClassroom::getClassroomId).containsExactly(turma3, turma2, turma1);
	}

	@Test
	void shouldCountActiveChildrenByGenderWhenCountingClassrooms() {
		// given
		UUID turma1 = insertClassroom("Turma 1");
		UUID turma2 = insertClassroom("Turma 2");
		UUID empty = insertClassroom("Turma 3");
		insertLink(saveChild(aChild().name("A").gender(Gender.MALE)), turma1, OCTOBER_1ST, null);
		insertLink(saveChild(aChild().name("B").gender(Gender.MALE)), turma1, OCTOBER_1ST, null);
		insertLink(saveChild(aChild().name("C").gender(Gender.FEMALE)), turma1, OCTOBER_1ST, null);
		insertLink(saveChild(aChild().name("D")), turma1, OCTOBER_1ST, null);
		UUID left = saveChild(aChild().name("E").gender(Gender.FEMALE));
		insertLink(left, turma1, OCTOBER_1ST, NOVEMBER_1ST);
		insertLink(left, turma2, NOVEMBER_1ST, null);

		// when
		Map<UUID, ClassroomStudentCountRow> counts = linkRepository
			.countActiveByClassroomIdIn(List.of(turma1, turma2, empty))
			.stream()
			.collect(Collectors.toMap(ClassroomStudentCountRow::getClassroomId, Function.identity()));

		// then
		assertThat(counts).containsOnlyKeys(turma1, turma2);
		assertThat(counts.get(turma1)).extracting(ClassroomStudentCountRow::getTotal,
				ClassroomStudentCountRow::getMale, ClassroomStudentCountRow::getFemale).containsExactly(4L, 2L, 1L);
		assertThat(counts.get(turma2)).extracting(ClassroomStudentCountRow::getTotal,
				ClassroomStudentCountRow::getMale, ClassroomStudentCountRow::getFemale).containsExactly(1L, 0L, 1L);
	}

	@Test
	void shouldTellWhetherSomeChildLeftClassroom() {
		// given
		UUID onlyActive = insertClassroom("Turma 1");
		UUID withHistory = insertClassroom("Turma 2");
		insertLink(saveChild(aChild().name("A")), onlyActive, OCTOBER_1ST, null);
		insertLink(saveChild(aChild().name("B")), withHistory, OCTOBER_1ST, NOVEMBER_1ST);

		// when / then
		assertThat(linkRepository.existsByClassroomIdAndEndDateIsNotNull(onlyActive)).isFalse();
		assertThat(linkRepository.existsByClassroomIdAndEndDateIsNotNull(withHistory)).isTrue();
	}

	@Test
	void shouldRecordAuthorsWhenLinkIsSaved() {
		// given
		UUID child = saveChild(aChild());

		// when
		ChildClassroom link = save(ChildClassroom.open(child, insertClassroom("Turma 1"), OCTOBER_1ST));

		// then
		assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM child_classrooms WHERE id = ?", UUID.class,
				link.getId())).isEqualTo(ANA_ID);
	}

	private static ChildClassroom closed(ChildClassroom link, LocalDate endDate) {
		link.close(endDate);
		return link;
	}

	private ChildClassroom save(ChildClassroom link) {
		return AuthenticatedAs.call(ANA_ID, () -> linkRepository.saveAndFlush(link));
	}

	private UUID saveChild(ChildDetailsBuilder details) {
		return AuthenticatedAs.call(ANA_ID, () -> childRepository.saveAndFlush(new Child(details.build()))).getId();
	}

	private UUID insertClassroom(String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO classrooms (id, name, created_at, updated_at, created_by, updated_by)
				VALUES (?, ?, now(), now(), ?, ?)
				""", id, name, ANA_ID, ANA_ID);
		return id;
	}

	/**
	 * Inserts a link straight into the table; links of the same child are created at increasing instants, in the
	 * order of the calls.
	 */
	private void insertLink(UUID childId, UUID classroomId, LocalDate startDate, LocalDate endDate) {
		jdbcTemplate.update("""
				INSERT INTO child_classrooms (id, child_id, classroom_id, start_date, end_date, created_at, updated_at,
				                              created_by, updated_by)
				VALUES (?, ?, ?, ?, ?, clock_timestamp(), clock_timestamp(), ?, ?)
				""", UUID.randomUUID(), childId, classroomId, Date.valueOf(startDate),
				endDate == null ? null : Date.valueOf(endDate), ANA_ID, ANA_ID);
	}

}
