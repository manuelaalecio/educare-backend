package com.manuelaalecio.educare_backend.guardian.domain;

import static com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder.aGuardian;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder;
import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.persistence.JpaAuditingConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.AuthenticatedAs;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every test runs in its own transaction, rolled back at the end. Guardians are saved inside {@link AuthenticatedAs},
 * because {@code created_by}/{@code updated_by} are NOT NULL.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})
class GuardianRepositoryTest {

	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000a1");

	@Autowired
	private GuardianRepository guardianRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void shouldReportCpfConstraintWhenCpfIsRepeated() {
		// given
		save(aGuardian().cpf("52998224725"));

		// when / then
		assertThatThrownBy(() -> save(aGuardian().name("Joana Lima").cpf("529.982.247-25")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.cause()
			.isInstanceOfSatisfying(ConstraintViolationException.class, violation -> assertThat(
					violation.getConstraintName()).isEqualTo(GuardianRepository.CPF_UNIQUE_CONSTRAINT));
	}

	@Test
	void shouldAcceptManyGuardiansWhenCpfIsMissing() {
		// when
		save(aGuardian().name("Ana"));
		save(aGuardian().name("Bruno"));

		// then
		assertThat(guardianRepository.count()).isEqualTo(2);
	}

	@Test
	void shouldFindCpfOfOtherGuardiansOnlyWhenCheckingExceptId() {
		// given
		Guardian maria = save(aGuardian().cpf("52998224725"));
		Guardian joao = save(aGuardian().name("João"));

		// then
		assertThat(guardianRepository.existsByCpf("52998224725")).isTrue();
		assertThat(guardianRepository.existsByCpf("11144477735")).isFalse();
		assertThat(guardianRepository.existsByCpfAndIdNot("52998224725", maria.getId())).isFalse();
		assertThat(guardianRepository.existsByCpfAndIdNot("52998224725", joao.getId())).isTrue();
	}

	@Test
	void shouldRejectInsertWhenNotWorkingHasWorkplace() {
		assertThatThrownBy(() -> insert(true, "Padaria", null, null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("guardians_work_check");
	}

	@Test
	void shouldRejectInsertWhenWorkingWithoutWorkPhone() {
		assertThatThrownBy(() -> insert(false, "Padaria", "Rua A, 1", null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("guardians_work_check");
	}

	@Test
	void shouldAcceptInsertWhenWorkFieldsMatchNotWorking() {
		// when
		insert(true, null, null, null);
		insert(false, "Padaria", "Rua A, 1", "1133334444");

		// then
		assertThat(guardianRepository.count()).isEqualTo(2);
	}

	@Test
	void shouldFindNamesIgnoringAccentsAndCaseWhenSearching() {
		// given
		save(aGuardian().name("João Pedro"));
		save(aGuardian().name("Joana Lima"));
		save(aGuardian().name("Maria João"));

		// when
		Page<Guardian> page = guardianRepository.findAll(GuardianSpecifications.nameMatches("%joao%"),
				PageRequest.of(0, 20, Sort.by("name")));

		// then
		assertThat(page.getContent()).extracting(Guardian::getName).containsExactly("João Pedro", "Maria João");
	}

	@Test
	void shouldTreatEscapedPercentAsLiteralWhenSearching() {
		// given
		save(aGuardian().name("Ana"));
		save(aGuardian().name("Ana 100%"));

		// when
		Page<Guardian> page = guardianRepository.findAll(GuardianSpecifications.nameMatches("%\\%%"),
				PageRequest.of(0, 20));

		// then
		assertThat(page.getContent()).extracting(Guardian::getName).containsExactly("Ana 100%");
	}

	@Test
	void shouldFindOnlyExactCpfWhenFilteringByCpf() {
		// given
		save(aGuardian().name("João Lima").cpf("52998224725"));
		save(aGuardian().name("Maria Souza"));

		// when
		Page<Guardian> exact = guardianRepository.findAll(GuardianSpecifications.cpfEquals("52998224725"),
				PageRequest.of(0, 20));
		Page<Guardian> part = guardianRepository.findAll(GuardianSpecifications.cpfEquals("529982"),
				PageRequest.of(0, 20));

		// then
		assertThat(exact.getContent()).extracting(Guardian::getName).containsExactly("João Lima");
		assertThat(part.getContent()).isEmpty();
	}

	private Guardian save(GuardianDetailsBuilder details) {
		return AuthenticatedAs.call(ANA_ID, () -> guardianRepository.saveAndFlush(new Guardian(details.build())));
	}

	/**
	 * Inserts a guardian straight into the table (bypassing the entity's own validation) to exercise the check of
	 * the work fields.
	 */
	private void insert(boolean notWorking, String workplace, String workAddress, String workPhone) {
		jdbcTemplate.update("""
				INSERT INTO guardians (id, name, phone, not_working, workplace, work_address, work_phone, created_at,
				                       updated_at, created_by, updated_by)
				VALUES (?, 'Maria', '11912345678', ?, ?, ?, ?, now(), now(), ?, ?)
				""", UUID.randomUUID(), notWorking, workplace, workAddress, workPhone, ANA_ID, ANA_ID);
	}

}
