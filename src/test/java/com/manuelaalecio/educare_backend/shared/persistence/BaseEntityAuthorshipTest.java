package com.manuelaalecio.educare_backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.AuthenticatedAs;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.persistence.TestAuditedEntity;
import com.manuelaalecio.educare_backend.shared.testsupport.persistence.TestAuditedEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Authorship of {@link BaseEntity} ({@code created_by}/{@code updated_by}) through {@code test_audited_entity}, with
 * the auditor of {@link JpaAuditingConfiguration} and the user set by {@link AuthenticatedAs}. Values are read back
 * from the database, not from the entity.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})
class BaseEntityAuthorshipTest {

	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID BRUNO_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");

	@Autowired
	private TestAuditedEntityRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void shouldRecordUserAsBothAuthorsWhenRecordIsSavedByUser() {
		// when
		TestAuditedEntity saved = AuthenticatedAs.call(ANA_ID,
				() -> repository.saveAndFlush(new TestAuditedEntity("Ana")));

		// then
		assertThat(authorInDatabase("created_by", saved.getId())).isEqualTo(ANA_ID);
		assertThat(authorInDatabase("updated_by", saved.getId())).isEqualTo(ANA_ID);
	}

	@Test
	void shouldChangeOnlyLastModifiedAuthorWhenAnotherUserChangesRecord() {
		// given
		TestAuditedEntity saved = AuthenticatedAs.call(ANA_ID,
				() -> repository.saveAndFlush(new TestAuditedEntity("Ana")));

		// when
		AuthenticatedAs.run(BRUNO_ID, () -> {
			saved.rename("Ana Maria");
			repository.saveAndFlush(saved);
		});

		// then
		assertThat(authorInDatabase("created_by", saved.getId())).isEqualTo(ANA_ID);
		assertThat(authorInDatabase("updated_by", saved.getId())).isEqualTo(BRUNO_ID);
	}

	@Test
	void shouldLeaveBothAuthorsEmptyWhenRecordIsSavedWithoutUser() {
		// when
		TestAuditedEntity saved = repository.saveAndFlush(new TestAuditedEntity("Ana"));

		// then
		assertThat(authorInDatabase("created_by", saved.getId())).isNull();
		assertThat(authorInDatabase("updated_by", saved.getId())).isNull();
	}

	@Test
	void shouldKeepOriginalCreationAuthorWhenCreationAuthorIsTamperedBeforeSaving() {
		// given
		TestAuditedEntity saved = AuthenticatedAs.call(ANA_ID,
				() -> repository.saveAndFlush(new TestAuditedEntity("Ana")));
		ReflectionTestUtils.setField(saved, "createdBy", BRUNO_ID);

		// when
		AuthenticatedAs.run(BRUNO_ID, () -> {
			saved.rename("Ana Maria");
			repository.saveAndFlush(saved);
		});

		// then
		assertThat(authorInDatabase("created_by", saved.getId())).isEqualTo(ANA_ID);
		assertThat(authorInDatabase("updated_by", saved.getId())).isEqualTo(BRUNO_ID);
	}

	private UUID authorInDatabase(String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM test_audited_entity WHERE id = ?", UUID.class, id);
	}

}
