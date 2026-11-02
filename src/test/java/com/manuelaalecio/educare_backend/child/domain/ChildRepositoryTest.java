package com.manuelaalecio.educare_backend.child.domain;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.BIRTH_CERTIFICATE_NUMBER;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.ChildDetailsBuilder;
import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.persistence.JpaAuditingConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.AuthenticatedAs;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every test runs in its own transaction, rolled back at the end. Children are saved inside {@link AuthenticatedAs},
 * because {@code created_by}/{@code updated_by} are NOT NULL.
 */
@DataJpaTest
// the JPA slice does not scan @Configuration classes, so auditing is imported explicitly
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})
class ChildRepositoryTest {

	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000a1");
	private static final UUID BRUNO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000b1");

	@Autowired
	private ChildRepository childRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void shouldFindNamesIgnoringAccentsAndCaseWhenSearching() {
		// given
		save(aChild().name("João Pedro"));
		save(aChild().name("Joana Lima"));
		save(aChild().name("Maria João"));

		// when
		Page<Child> page = childRepository.findAll(ChildSpecifications.nameMatches("%joao%"),
				PageRequest.of(0, 20, Sort.by("name")));

		// then
		assertThat(page.getContent()).extracting(Child::getName).containsExactly("João Pedro", "Maria João");
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void shouldTreatEscapedWildcardAsLiteralWhenSearching() {
		// given
		save(aChild().name("Ana"));
		save(aChild().name("Bruno"));

		// when
		Page<Child> percent = childRepository.findAll(ChildSpecifications.nameMatches("%\\%%"), PageRequest.of(0, 20));
		Page<Child> underscore = childRepository.findAll(ChildSpecifications.nameMatches("%\\_%"), PageRequest.of(0, 20));

		// then
		assertThat(percent.getContent()).isEmpty();
		assertThat(underscore.getContent()).isEmpty();
	}

	@Test
	void shouldPageAndSortWhenSearching() {
		// given
		save(aChild().name("Ana Clara"));
		save(aChild().name("Bruna Clara"));
		save(aChild().name("Carla Clara"));

		// when
		Page<Child> page = childRepository.findAll(ChildSpecifications.nameMatches("%clara%"),
				PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "name")));

		// then
		assertThat(page.getContent()).extracting(Child::getName).containsExactly("Carla Clara", "Bruna Clara");
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getTotalPages()).isEqualTo(2);
	}

	@Test
	void shouldFindOnlyChildrenCurrentlyInClassroomWhenFilteringByClassroom() {
		// given
		UUID turma1 = insertClassroom("Turma 1");
		UUID turma2 = insertClassroom("Turma 2");
		link(save(aChild().name("João Pedro")), turma1, null);
		link(save(aChild().name("Ana Lima")), turma1, null);
		link(save(aChild().name("Maria João")), turma2, null);
		Child joaoPaulo = save(aChild().name("João Paulo"));
		link(joaoPaulo, turma1, LocalDate.of(2026, 11, 1));
		link(joaoPaulo, turma2, null);

		// when
		Page<Child> inTurma1 = childRepository.findAll(ChildSpecifications.inClassroom(turma1),
				PageRequest.of(0, 20, Sort.by("name")));
		Page<Child> joaoInTurma1 = childRepository.findAll(
				ChildSpecifications.inClassroom(turma1).and(ChildSpecifications.nameMatches("%joao%")),
				PageRequest.of(0, 20, Sort.by("name")));

		// then
		assertThat(inTurma1.getContent()).extracting(Child::getName).containsExactly("Ana Lima", "João Pedro");
		assertThat(joaoInTurma1.getContent()).extracting(Child::getName).containsExactly("João Pedro");
		assertThat(joaoInTurma1.getTotalElements()).isEqualTo(1);
	}

	@Test
	void shouldFindNothingWhenClassroomHasNoChildren() {
		// given
		UUID empty = insertClassroom("Turma 3");
		link(save(aChild()), insertClassroom("Turma 1"), null);

		// when
		Page<Child> page = childRepository.findAll(ChildSpecifications.inClassroom(empty), PageRequest.of(0, 20));

		// then
		assertThat(page.getContent()).isEmpty();
	}

	@Test
	void shouldFindDocumentsOfOtherChildrenOnlyWhenCheckingExceptId() {
		// given
		Child maria = save(aChild().cpf("52998224725").rg("12345678X").birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER));
		Child joao = save(aChild().name("João"));

		// then
		assertThat(childRepository.existsByCpf("52998224725")).isTrue();
		assertThat(childRepository.existsByCpf("11144477735")).isFalse();
		assertThat(childRepository.existsByCpfAndIdNot("52998224725", maria.getId())).isFalse();
		assertThat(childRepository.existsByCpfAndIdNot("52998224725", joao.getId())).isTrue();
		assertThat(childRepository.existsByRg("12345678X")).isTrue();
		assertThat(childRepository.existsByRgAndIdNot("12345678X", maria.getId())).isFalse();
		assertThat(childRepository.existsByRgAndIdNot("12345678X", joao.getId())).isTrue();
		assertThat(childRepository.existsByBirthCertificateNumber(BIRTH_CERTIFICATE_NUMBER)).isTrue();
		assertThat(childRepository.existsByBirthCertificateNumberAndIdNot(BIRTH_CERTIFICATE_NUMBER, maria.getId()))
			.isFalse();
		assertThat(childRepository.existsByBirthCertificateNumberAndIdNot(BIRTH_CERTIFICATE_NUMBER, joao.getId()))
			.isTrue();
	}

	@Test
	void shouldReportCpfConstraintWhenCpfIsRepeated() {
		// given
		save(aChild().cpf("52998224725"));

		// when / then
		assertViolates(ChildRepository.CPF_UNIQUE_CONSTRAINT, aChild().name("Outra").cpf("529.982.247-25"));
	}

	@Test
	void shouldReportRgConstraintWhenRgIsRepeated() {
		// given
		save(aChild().rg("12345678X"));

		// when / then
		assertViolates(ChildRepository.RG_UNIQUE_CONSTRAINT, aChild().name("Outra").rg("12345678-x"));
	}

	@Test
	void shouldReportBirthCertificateConstraintWhenNumberIsRepeated() {
		// given
		save(aChild().birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER));

		// when / then
		assertViolates(ChildRepository.BIRTH_CERTIFICATE_NUMBER_UNIQUE_CONSTRAINT,
				aChild().name("Outra").birthCertificateNumber("104539 01 55 2013 1 00012 021 0000123 21"));
	}

	@Test
	void shouldAcceptManyChildrenWhenDocumentsAreMissing() {
		// when
		save(aChild().name("Ana"));
		save(aChild().name("Bruno"));

		// then
		assertThat(childRepository.count()).isEqualTo(2);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"rg | '12.345'",
			"state | 'XX'",
			"race | 'brown'",
			"gender | 'F'",
			"marital_status | 'DATING'",
			"cep | '01310-100'",
			"cpf | '529.982.247-25'",
			"birth_certificate_number | '1045390155201310001202100001232'",
			"birth_certificate_number | '1045390155201310001202100001232X'"})
	void shouldRejectInsertWhenCheckedColumnIsInvalid(String column, String value) {
		// when / then
		assertThatThrownBy(() -> insertWith(column, value)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shouldAcceptInsertWhenCheckedColumnsAreValid() {
		// when
		insertWith("birth_certificate_number", BIRTH_CERTIFICATE_NUMBER);

		// then
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM children", Integer.class)).isEqualTo(1);
	}

	@Test
	void shouldRecordAuthorsWhenSavedAndUpdated() {
		// given
		Child saved = save(aChild());

		// when
		AuthenticatedAs.run(BRUNO_ID, () -> {
			saved.update(aChild().neighborhood("Consolação").build());
			childRepository.saveAndFlush(saved);
		});

		// then
		assertThat(authorInDatabase("created_by", saved.getId())).isEqualTo(ANA_ID);
		assertThat(authorInDatabase("updated_by", saved.getId())).isEqualTo(BRUNO_ID);
	}

	@Test
	void shouldRejectSaveWhenNoUserIsAuthenticated() {
		// when / then
		assertThatThrownBy(() -> childRepository.saveAndFlush(new Child(aChild().build())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shouldStoreEnumsAsTextWhenSaved() {
		// given
		Child saved = save(aChild().race(Race.INDIGENOUS).gender(Gender.OTHER).maritalStatus(MaritalStatus.STABLE_UNION));

		// when
		var row = jdbcTemplate.queryForMap("SELECT state, race, gender, marital_status FROM children WHERE id = ?",
				saved.getId());
		Child read = childRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(row).containsEntry("state", "SP")
			.containsEntry("race", "INDIGENOUS")
			.containsEntry("gender", "OTHER")
			.containsEntry("marital_status", "STABLE_UNION");
		assertThat(read.getRace()).isEqualTo(Race.INDIGENOUS);
		assertThat(read.getMaritalStatus()).isEqualTo(MaritalStatus.STABLE_UNION);
	}

	private Child save(ChildDetailsBuilder details) {
		return AuthenticatedAs.call(ANA_ID, () -> childRepository.saveAndFlush(new Child(details.build())));
	}

	private void assertViolates(String constraint, ChildDetailsBuilder details) {
		assertThatThrownBy(() -> save(details)).isInstanceOf(DataIntegrityViolationException.class)
			.cause()
			.isInstanceOfSatisfying(ConstraintViolationException.class,
					violation -> assertThat(violation.getConstraintName()).isEqualTo(constraint));
	}

	/**
	 * Inserts a valid child, replacing one column by the given value, straight into the table (bypassing the
	 * entity's own validation) to exercise the database constraints.
	 */
	private void insertWith(String column, String value) {
		var values = new LinkedHashMap<String, Object>();
		values.put("id", UUID.randomUUID());
		values.put("name", "Maria");
		values.put("birth_date", Date.valueOf("2018-05-10"));
		values.put("cep", "01310100");
		values.put("address", "Avenida Paulista");
		values.put("neighborhood", "Bela Vista");
		values.put("address_number", "1000");
		values.put("city", "São Paulo");
		values.put("state", "SP");
		values.put(column, value);
		String columns = String.join(", ", values.keySet()) + ", created_at, updated_at, created_by, updated_by";
		String placeholders = "?, ".repeat(values.size()) + "now(), now(), ?, ?";
		var args = new ArrayList<>(values.values());
		args.add(ANA_ID);
		args.add(ANA_ID);
		jdbcTemplate.update("INSERT INTO children (" + columns + ") VALUES (" + placeholders + ")", args.toArray());
	}

	private UUID insertClassroom(String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO classrooms (id, name, created_at, updated_at, created_by, updated_by)
				VALUES (?, ?, now(), now(), ?, ?)
				""", id, name, ANA_ID, ANA_ID);
		return id;
	}

	private void link(Child child, UUID classroomId, LocalDate endDate) {
		jdbcTemplate.update("""
				INSERT INTO child_classrooms (id, child_id, classroom_id, start_date, end_date, created_at, updated_at,
				                              created_by, updated_by)
				VALUES (?, ?, ?, ?, ?, now(), now(), ?, ?)
				""", UUID.randomUUID(), child.getId(), classroomId, Date.valueOf("2026-10-01"),
				endDate == null ? null : Date.valueOf(endDate), ANA_ID, ANA_ID);
	}

	private UUID authorInDatabase(String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM children WHERE id = ?", UUID.class, id);
	}

}
