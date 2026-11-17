package com.manuelaalecio.educare_backend.child.domain;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

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
 * {@link AuthenticatedAs}, because {@code created_by}/{@code updated_by} are NOT NULL; guardians belong to another
 * module, so they are inserted straight into the table.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})
class ChildGuardianRepositoryTest {

	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000a1");

	@Autowired
	private ChildGuardianRepository linkRepository;

	@Autowired
	private ChildRepository childRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void shouldRejectSecondLinkWhenPairIsRepeated() {
		// given
		UUID child = saveChild(aChild());
		UUID maria = insertGuardian("Maria Souza");
		save(ChildGuardian.link(child, maria, Relationship.MOTHER));

		// when / then
		assertThatThrownBy(() -> save(ChildGuardian.link(child, maria, Relationship.OTHER)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("child_guardians_child_guardian_key");
	}

	@Test
	void shouldDeleteLinksAndKeepGuardianWhenChildIsDeleted() {
		// given
		UUID child = saveChild(aChild());
		UUID maria = insertGuardian("Maria Souza");
		save(ChildGuardian.link(child, maria, Relationship.MOTHER));

		// when
		jdbcTemplate.update("DELETE FROM children WHERE id = ?", child);

		// then
		assertThat(count("SELECT count(*) FROM child_guardians WHERE child_id = ?", child)).isZero();
		assertThat(count("SELECT count(*) FROM guardians WHERE id = ?", maria)).isOne();
	}

	@Test
	void shouldRejectGuardianDeletionWhenGuardianIsLinked() {
		// given
		UUID maria = insertGuardian("Maria Souza");
		save(ChildGuardian.link(saveChild(aChild()), maria, Relationship.MOTHER));

		// when / then
		assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM guardians WHERE id = ?", maria))
			.isInstanceOf(DataIntegrityViolationException.class)
			.rootCause()
			.isInstanceOfSatisfying(SQLException.class,
					sql -> assertThat(sql.getSQLState()).isEqualTo("23503"));
	}

	@Test
	void shouldRejectInsertWhenRelationshipIsNotInList() {
		// given
		UUID child = saveChild(aChild());
		UUID maria = insertGuardian("Maria Souza");

		// when / then
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO child_guardians (id, child_id, guardian_id, relationship, created_at, updated_at,
				                             created_by, updated_by)
				VALUES (?, ?, ?, 'COUSIN', now(), now(), ?, ?)
				""", UUID.randomUUID(), child, maria, ANA_ID, ANA_ID))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("child_guardians_relationship_check");
	}

	@Test
	void shouldFindLinksOfTheChildOnlyWhenSearchingByChild() {
		// given
		UUID ana = saveChild(aChild().name("Ana Souza"));
		UUID pedro = saveChild(aChild().name("Pedro Souza"));
		UUID maria = insertGuardian("Maria Souza");
		UUID joao = insertGuardian("João Souza");
		save(ChildGuardian.link(ana, maria, Relationship.MOTHER));
		save(ChildGuardian.link(ana, joao, Relationship.FATHER));
		save(ChildGuardian.link(pedro, maria, Relationship.MOTHER));

		// when
		List<ChildGuardian> links = linkRepository.findByChildId(ana);

		// then
		assertThat(links).extracting(ChildGuardian::getGuardianId).containsExactlyInAnyOrder(maria, joao);
	}

	@Test
	void shouldFindChildrenOfSeveralGuardiansByChildNameWhenSearchingInBatch() {
		// given
		UUID pedro = saveChild(aChild().name("Pedro Souza"));
		UUID ana = saveChild(aChild().name("Ana Souza"));
		UUID bia = saveChild(aChild().name("Bia Lima"));
		UUID maria = insertGuardian("Maria Souza");
		UUID joao = insertGuardian("João Lima");
		UUID other = insertGuardian("Outra");
		save(ChildGuardian.link(pedro, maria, Relationship.STEPMOTHER));
		save(ChildGuardian.link(ana, maria, Relationship.MOTHER));
		save(ChildGuardian.link(bia, joao, Relationship.FATHER));
		save(ChildGuardian.link(bia, other, Relationship.OTHER));

		// when
		List<GuardianChildRow> rows = linkRepository.findChildrenOfGuardians(List.of(maria, joao));

		// then
		assertThat(rows).extracting(GuardianChildRow::getGuardianId, GuardianChildRow::getChildId,
				GuardianChildRow::getChildName, GuardianChildRow::getRelationship)
			.containsExactly(
					tuple(maria, ana, "Ana Souza", Relationship.MOTHER),
					tuple(joao, bia, "Bia Lima", Relationship.FATHER),
					tuple(maria, pedro, "Pedro Souza", Relationship.STEPMOTHER));
	}

	private void save(ChildGuardian link) {
		AuthenticatedAs.run(ANA_ID, () -> linkRepository.saveAndFlush(link));
	}

	private UUID saveChild(ChildDetailsBuilder details) {
		return AuthenticatedAs.call(ANA_ID, () -> childRepository.saveAndFlush(new Child(details.build()))).getId();
	}

	private UUID insertGuardian(String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO guardians (id, name, phone, not_working, created_at, updated_at, created_by, updated_by)
				VALUES (?, ?, '11912345678', true, now(), now(), ?, ?)
				""", id, name, ANA_ID, ANA_ID);
		return id;
	}

	private int count(String sql, UUID id) {
		return jdbcTemplate.queryForObject(sql, Integer.class, id);
	}

}
