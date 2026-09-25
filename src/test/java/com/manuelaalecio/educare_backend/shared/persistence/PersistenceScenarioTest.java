package com.manuelaalecio.educare_backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.persistence.TestAuditedEntity;
import com.manuelaalecio.educare_backend.shared.testsupport.persistence.TestAuditedEntityRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class PersistenceScenarioTest {

	private static final Instant CREATED_AT = Instant.parse("2026-01-10T12:00:00Z");
	private static final Instant MODIFIED_AT = Instant.parse("2026-01-11T08:30:00Z");

	@Autowired
	private TestAuditedEntityRepository repository;

	@Autowired
	private MutableClock clock;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		clock.setInstant(CREATED_AT);
	}

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	@Test
	@DisplayName("Scenario: Registro novo recebe identificador")
	void shouldAssignIdentifierWhenNewRecordIsSaved() {
		// given
		var entity = new TestAuditedEntity("Ana");

		// when
		var saved = repository.saveAndFlush(entity);

		// then
		assertThat(saved.getId()).isNotNull();
		assertThat(repository.findById(saved.getId()))
				.hasValueSatisfying(found -> {
					assertThat(found.getId()).isEqualTo(saved.getId());
					assertThat(found.getName()).isEqualTo("Ana");
				});
	}

	@Test
	@DisplayName("Scenario: Registros diferentes recebem identificadores diferentes")
	void shouldAssignDifferentIdentifiersWhenTwoRecordsAreSaved() {
		// when
		var first = repository.saveAndFlush(new TestAuditedEntity("Ana"));
		var second = repository.saveAndFlush(new TestAuditedEntity("Bruno"));

		// then
		assertThat(first.getId()).isNotNull();
		assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());
	}

	@Test
	@DisplayName("Scenario: Registro novo recebe as duas datas")
	void shouldFillBothDatesWithClockInstantWhenRecordIsFirstSaved() {
		// when
		var saved = repository.saveAndFlush(new TestAuditedEntity("Ana"));

		// then
		assertThat(createdAtInDatabase(saved.getId())).isEqualTo(CREATED_AT);
		assertThat(updatedAtInDatabase(saved.getId())).isEqualTo(CREATED_AT);
	}

	@Test
	@DisplayName("Scenario: Alteração atualiza só a data de última alteração")
	void shouldUpdateOnlyLastModifiedDateWhenRecordIsChanged() {
		// given
		var saved = repository.saveAndFlush(new TestAuditedEntity("Ana"));
		clock.setInstant(MODIFIED_AT);

		// when
		var loaded = repository.findById(saved.getId()).orElseThrow();
		loaded.rename("Ana Maria");
		repository.saveAndFlush(loaded);

		// then
		assertThat(updatedAtInDatabase(saved.getId())).isEqualTo(MODIFIED_AT);
		assertThat(createdAtInDatabase(saved.getId())).isEqualTo(CREATED_AT);
	}

	@Test
	@DisplayName("Scenario: Tentativa de alterar a data de criação é ignorada")
	void shouldKeepOriginalCreationDateWhenCreationDateIsTamperedBeforeSaving() {
		// given
		var saved = repository.saveAndFlush(new TestAuditedEntity("Ana"));
		clock.setInstant(MODIFIED_AT);
		var loaded = repository.findById(saved.getId()).orElseThrow();
		ReflectionTestUtils.setField(loaded, "createdAt", Instant.parse("2020-01-01T00:00:00Z"));
		loaded.rename("Ana Maria");

		// when
		repository.saveAndFlush(loaded);

		// then
		assertThat(createdAtInDatabase(saved.getId())).isEqualTo(CREATED_AT);
		assertThat(updatedAtInDatabase(saved.getId())).isEqualTo(MODIFIED_AT);
	}

	@Test
	@DisplayName("Scenario: Migrations íntegras são validadas")
	void shouldPassValidationWhenHistoryMatchesProjectMigrations() {
		// when
		var result = flyway.validateWithResult();

		// then
		assertThat(result.validationSuccessful).isTrue();
		assertThat(result.invalidMigrations).isEmpty();
	}

	@Test
	@DisplayName("Scenario: Banco migrado permanece intacto sem pedido de limpeza")
	void shouldKeepHistoryAndDataWhenApplicationStartsAndMigrates() {
		// given
		var saved = repository.saveAndFlush(new TestAuditedEntity("Ana"));
		var historyBefore = schemaHistory();

		// when
		flyway.migrate();

		// then
		assertThat(historyBefore)
				.anySatisfy(row -> {
					assertThat(row.get("version")).isEqualTo("1");
					assertThat(row.get("success")).isEqualTo(true);
				});
		assertThat(schemaHistory()).isEqualTo(historyBefore);
		assertThat(repository.findById(saved.getId())).isPresent();
	}

	@Test
	@DisplayName("Scenario: Pedido de limpeza é recusado")
	void shouldRejectCleanWhenCleanIsDisabled() {
		// given
		var historyBefore = schemaHistory();

		// when / then
		assertThatThrownBy(flyway::clean)
				.isInstanceOf(FlywayException.class)
				.hasMessageContainingAll("clean", "disabled");
		assertThat(schemaHistory()).isEqualTo(historyBefore);
	}

	@Test
	@DisplayName("Scenario: Entidades compatíveis com o schema")
	void shouldHaveOnlyMigrationTablesWhenApplicationStartsWithCompatibleEntities() {
		// when
		var tables = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
				String.class);

		// then
		assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "test_audited_entity");
	}

	private Instant createdAtInDatabase(UUID id) {
		return timestampInDatabase("created_at", id);
	}

	private Instant updatedAtInDatabase(UUID id) {
		return timestampInDatabase("updated_at", id);
	}

	private Instant timestampInDatabase(String column, UUID id) {
		return jdbcTemplate.queryForObject(
				"SELECT " + column + " FROM test_audited_entity WHERE id = ?", OffsetDateTime.class, id)
				.toInstant();
	}

	private List<Map<String, Object>> schemaHistory() {
		return jdbcTemplate.queryForList(
				"SELECT installed_rank, version, checksum, success FROM flyway_schema_history ORDER BY installed_rank");
	}

}
