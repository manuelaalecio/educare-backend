package com.manuelaalecio.educare_backend.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.persistence.JpaAuditingConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every test database already contains the admin seeded by V3 ({@code admin@educare.org}). These tests use other
 * logins and never count users, and each one rolls back its own changes, so they do not depend on the seed
 * (except the seed test itself). The concurrency test commits its transactions, so it cleans up what it changes.
 */
@DataJpaTest
// the JPA slice does not scan @Configuration classes, so auditing (created_at/updated_at) is imported explicitly
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})
class UserRepositoryTest {

	private static final String INSERT_WITH_ROLE = "INSERT INTO users "
			+ "(id, name, login, password_hash, role, created_at, updated_at) "
			+ "VALUES (?, 'Ana Souza', 'ana@educare.org', 'hash', ?, now(), now())";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void shouldFindLoginWhenUserWithLoginExists() {
		// given
		userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash"));

		// when
		boolean exists = userRepository.existsByLogin("ana@educare.org");

		// then
		assertThat(exists).isTrue();
	}

	@Test
	void shouldNotFindLoginWhenNoUserHasLogin() {
		// given
		userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash"));

		// when
		boolean exists = userRepository.existsByLogin("bruno@educare.org");

		// then
		assertThat(exists).isFalse();
	}

	@Test
	void shouldFindLoginExcludingIdWhenAnotherUserHasLogin() {
		// given
		userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash"));
		User bruno = userRepository.saveAndFlush(new User("Bruno Lima", "bruno@educare.org", "hash"));

		// when
		boolean exists = userRepository.existsByLoginAndIdNot("ana@educare.org", bruno.getId());

		// then
		assertThat(exists).isTrue();
	}

	@Test
	void shouldNotFindLoginExcludingIdWhenOnlyTheExcludedUserHasLogin() {
		// given
		User ana = userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash"));

		// when
		boolean exists = userRepository.existsByLoginAndIdNot("ana@educare.org", ana.getId());

		// then
		assertThat(exists).isFalse();
	}

	@Test
	void shouldNotFindLoginExcludingIdWhenNoUserHasLogin() {
		// given
		userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash"));

		// when
		boolean exists = userRepository.existsByLoginAndIdNot("bruno@educare.org", UUID.randomUUID());

		// then
		assertThat(exists).isFalse();
	}

	@Test
	void shouldViolateLoginUniqueConstraintWhenTwoUsersHaveSameLogin() {
		// given
		userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash"));
		User duplicate = new User("Ana Lima", "ana@educare.org", "other-hash");

		// when / then
		assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("users_login_key");
	}

	@Test
	void shouldViolateRoleCheckConstraintWhenRoleIsUnknown() {
		// given
		UUID id = UUID.randomUUID();

		// when / then
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_WITH_ROLE, id, "SUPER"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("users_role_check");
	}

	@Test
	void shouldStoreUserRoleWhenRoleColumnIsOmitted() {
		// given
		UUID id = UUID.randomUUID();

		// when
		jdbcTemplate.update("INSERT INTO users (id, name, login, password_hash, created_at, updated_at) "
				+ "VALUES (?, 'Ana Souza', 'ana@educare.org', 'hash', now(), now())", id);

		// then
		assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id))
			.isEqualTo("USER");
	}

	@Test
	void shouldStoreRoleAsTextWhenUsersAreSaved() {
		// given
		User admin = new User("Ana Souza", "ana@educare.org", "hash", Role.ADMIN);
		User user = new User("Bruno Lima", "bruno@educare.org", "hash", Role.USER);

		// when
		userRepository.saveAndFlush(admin);
		userRepository.saveAndFlush(user);

		// then
		assertThat(storedRole(admin.getId())).isEqualTo("ADMIN");
		assertThat(storedRole(user.getId())).isEqualTo("USER");
	}

	@Test
	void shouldReadRoleAsEnumWhenStoredAsText() {
		// given
		UUID adminId = UUID.randomUUID();
		jdbcTemplate.update(INSERT_WITH_ROLE, adminId, "ADMIN");
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update(INSERT_WITH_ROLE.replace("ana@", "bruno@"), userId, "USER");
		entityManager.clear();

		// when
		User admin = userRepository.findById(adminId).orElseThrow();
		User user = userRepository.findById(userId).orElseThrow();

		// then
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(user.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void shouldMatchDevDefaultPasswordWhenAdminHashIsGeneratedByPgcrypto() {
		// given: the admin seeded by V3 with the dev default password (tests activate no profile, so dev applies)
		String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE login = ?",
				String.class, "admin@educare.org");
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

		// when
		boolean matches = passwordEncoder.matches("educare123", passwordHash);

		// then
		assertThat(passwordHash).startsWith("$2a$10$");
		assertThat(matches).isTrue();
		assertThat(passwordEncoder.matches("educare1234", passwordHash)).isFalse();
	}

	@Test
	void shouldFindUserByNormalizedLoginWhenLoginExists() {
		// given
		User ana = userRepository.saveAndFlush(new User("Ana Souza", "Ana@Educare.org", "hash"));

		// when
		var found = userRepository.findByLogin("ana@educare.org");

		// then
		assertThat(found).map(User::getId).contains(ana.getId());
	}

	@Test
	void shouldFindNoUserByLoginWhenNoUserHasLogin() {
		// given
		userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash"));

		// when
		var found = userRepository.findByLogin("bruno@educare.org");

		// then
		assertThat(found).isEmpty();
	}

	@Test
	void shouldReturnOnlyUsersWithRoleWhenFindingAllByRoleForUpdate() {
		// given: rolled back at the end of the test, like the users created here
		jdbcTemplate.update("DELETE FROM users");
		User ana = userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash", Role.ADMIN));
		User bruno = userRepository.saveAndFlush(new User("Bruno Lima", "bruno@educare.org", "hash", Role.ADMIN));
		userRepository.saveAndFlush(new User("Carla Dias", "carla@educare.org", "hash", Role.USER));

		// when
		List<User> admins = userRepository.findAllByRoleForUpdate(Role.ADMIN);

		// then
		assertThat(admins).extracting(User::getId).containsExactlyInAnyOrder(ana.getId(), bruno.getId());
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void shouldAcceptExactlyOneWhenTwoTransactionsDemoteDifferentAdminsConcurrently() throws Exception {
		// given: only two ADMINs; the ADMINs already stored (the seed) are demoted until the end of the test
		List<UUID> storedAdmins = jdbcTemplate.queryForList("SELECT id FROM users WHERE role = 'ADMIN'", UUID.class);
		jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE role = 'ADMIN'");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			User ana = userRepository.saveAndFlush(new User("Ana Souza", "ana@educare.org", "hash", Role.ADMIN));
			User bruno = userRepository.saveAndFlush(new User("Bruno Lima", "bruno@educare.org", "hash", Role.ADMIN));
			CyclicBarrier bothStarted = new CyclicBarrier(2);

			// when
			Future<Boolean> demoteAna = executor.submit(() -> demoteUnlessLastAdmin(ana.getId(), bothStarted));
			Future<Boolean> demoteBruno = executor.submit(() -> demoteUnlessLastAdmin(bruno.getId(), bothStarted));

			// then
			assertThat(List.of(demoteAna.get(30, TimeUnit.SECONDS), demoteBruno.get(30, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
			assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE role = 'ADMIN'", Integer.class))
				.isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
			jdbcTemplate.update("DELETE FROM users WHERE login IN ('ana@educare.org', 'bruno@educare.org')");
			storedAdmins.forEach(id -> jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", id));
		}
	}

	/**
	 * Does in its own transaction what {@code UserService} does before demoting an ADMIN. Both transactions are
	 * open, with their target already read, before either of them locks the ADMINs.
	 *
	 * @return whether the demotion was accepted
	 */
	private boolean demoteUnlessLastAdmin(UUID id, CyclicBarrier bothStarted) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			User user = userRepository.findById(id).orElseThrow();
			await(bothStarted);
			if (userRepository.findAllByRoleForUpdate(Role.ADMIN).size() < 2) {
				return false;
			}
			user.changeRole(Role.USER);
			userRepository.flush();
			return true;
		});
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException | BrokenBarrierException | TimeoutException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String storedRole(UUID id) {
		return jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
	}

}
