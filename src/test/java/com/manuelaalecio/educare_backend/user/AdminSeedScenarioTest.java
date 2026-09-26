package com.manuelaalecio.educare_backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.EducareBackendApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Scenarios of the requirement "Administrador inicial", which need to start the application more than once or with
 * the {@code prod} profile. Same pattern as {@code PersistenceStartupScenarioTest}: its own container and a fresh
 * database per test instead of {@code TestcontainersConfiguration} (design D8 of the add-user-crud change).
 */
@Testcontainers
class AdminSeedScenarioTest {

	private static final String ADMIN_PASSWORD_VARIABLE = "EDUCARE_ADMIN_PASSWORD";
	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String JWT_SECRET_VARIABLE = "EDUCARE_JWT_SECRET";
	private static final String CORS_ALLOWED_ORIGINS_VARIABLE = "EDUCARE_CORS_ALLOWED_ORIGINS";

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

	private String databaseUrl;

	@BeforeEach
	void createFreshDatabase() throws SQLException {
		String databaseName = "admin_seed_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + databaseName);
		}
		databaseUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + databaseName);
	}

	@Test
	@DisplayName("Scenario: Banco vazio recebe o administrador")
	void shouldSeedAdminWithConfiguredPasswordWhenDatabaseIsEmpty() throws SQLException {
		// given: a fresh, empty database

		// when
		try (ConfigurableApplicationContext context = startApplication("--" + ADMIN_PASSWORD_VARIABLE + "=senhaAdmin123")) {
			// then
			assertThat(context.isRunning()).isTrue();
			assertThat(query("SELECT name || ':' || role FROM users WHERE login = '" + ADMIN_LOGIN + "'"))
				.containsExactly("Administrador:ADMIN");
			assertThat(adminPasswordMatches("senhaAdmin123")).isTrue();
		}
	}

	@Test
	@DisplayName("Scenario: Administrador excluído não é recriado")
	void shouldNotRecreateAdminWhenApplicationRestartsAfterAdminWasDeleted() throws SQLException {
		// given
		startApplication().close();
		execute("DELETE FROM users WHERE login = '" + ADMIN_LOGIN + "'");

		// when
		try (ConfigurableApplicationContext context = startApplication()) {
			// then
			assertThat(context.isRunning()).isTrue();
			assertThat(query("SELECT id::text FROM users WHERE login = '" + ADMIN_LOGIN + "'")).isEmpty();
		}
	}

	@Test
	@DisplayName("Scenario: Produção sem senha do administrador")
	void shouldRefuseStartupWhenProdProfileHasNoAdminPassword() throws SQLException {
		// given: a fresh, empty database, the prod profile and no admin password in the environment

		// when / then
		assertThatThrownBy(() -> startApplication("--spring.profiles.active=prod"))
			.hasStackTraceContaining("Senha do administrador inicial não configurada");
		assertThat(query("SELECT id::text FROM users WHERE login = '" + ADMIN_LOGIN + "'")).isEmpty();
		assertThat(query("SELECT version FROM flyway_schema_history WHERE version = '3'")).isEmpty();
	}

	/**
	 * Docker compose passes an empty value when the variable is missing from the {@code .env}; V3 refuses it instead
	 * of creating an admin with an empty password.
	 */
	@Test
	void shouldRefuseStartupWhenAdminPasswordIsEmpty() throws SQLException {
		// given: a fresh, empty database

		// when / then
		assertThatThrownBy(() -> startApplication("--spring.profiles.active=prod", "--" + ADMIN_PASSWORD_VARIABLE + "="))
			.hasStackTraceContaining("Senha do administrador inicial não configurada");
		assertThat(query("SELECT id::text FROM users WHERE login = '" + ADMIN_LOGIN + "'")).isEmpty();
	}

	/**
	 * Regression test for the checksum risk of design D9: Flyway computes the checksum of V3 before replacing the
	 * placeholder, so changing the admin password after the seed was applied neither blocks the startup nor changes
	 * the admin.
	 */
	@Test
	void shouldStartWithUnchangedHistoryAndAdminWhenAdminPasswordChangesAfterSeed() throws SQLException {
		// given
		startApplication("--" + ADMIN_PASSWORD_VARIABLE + "=primeiraSenha1").close();
		List<String> historyBeforeRestart = history();

		// when
		try (ConfigurableApplicationContext context = startApplication("--" + ADMIN_PASSWORD_VARIABLE + "=segundaSenha2")) {
			// then
			assertThat(context.isRunning()).isTrue();
			assertThat(history()).isEqualTo(historyBeforeRestart);
			assertThat(adminPasswordMatches("primeiraSenha1")).isTrue();
			assertThat(adminPasswordMatches("segundaSenha2")).isFalse();
		}
	}

	private ConfigurableApplicationContext startApplication(String... extraArguments) {
		String[] arguments = Stream.concat(Stream.of(
				// command-line arguments, so they take precedence over application-dev.yaml and application-prod.yaml
				"--spring.datasource.url=" + databaseUrl,
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(),
				"--spring.devtools.restart.enabled=false",
				// valid security settings, so the prod scenarios fail only for the admin password
				"--" + JWT_SECRET_VARIABLE + "=" + "a".repeat(32),
				"--" + CORS_ALLOWED_ORIGINS_VARIABLE + "=https://educare.example.org"), Stream.of(extraArguments))
			.toArray(String[]::new);
		return new SpringApplicationBuilder(EducareBackendApplication.class)
			.web(WebApplicationType.NONE)
			.environment(environmentWithoutAdminPassword())
			.initializers(context -> context.getBeanFactory()
				.registerSingleton(ExcludeTestConfigurationsFilter.class.getName(), new ExcludeTestConfigurationsFilter()))
			.run(arguments);
	}

	/**
	 * The JVM environment cannot be changed, so the application gets a copy of it without the admin password and the
	 * security variables. This keeps the scenarios deterministic even when the variables are set where the tests run.
	 */
	private static StandardEnvironment environmentWithoutAdminPassword() {
		StandardEnvironment environment = new StandardEnvironment();
		Map<String, Object> variables = new HashMap<>(environment.getSystemEnvironment());
		variables.remove(ADMIN_PASSWORD_VARIABLE);
		variables.remove(JWT_SECRET_VARIABLE);
		variables.remove(CORS_ALLOWED_ORIGINS_VARIABLE);
		environment.getPropertySources()
			.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
		return environment;
	}

	private boolean adminPasswordMatches(String password) throws SQLException {
		List<String> hashes = query("SELECT password_hash FROM users WHERE login = '" + ADMIN_LOGIN + "'");
		assertThat(hashes).hasSize(1);
		return new BCryptPasswordEncoder().matches(password, hashes.getFirst());
	}

	private List<String> history() throws SQLException {
		return query("SELECT installed_rank || ':' || version || ':' || checksum || ':' || success "
				+ "FROM flyway_schema_history ORDER BY installed_rank");
	}

	private List<String> query(String sql) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection connection = openDatabaseConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				rows.add(resultSet.getString(1));
			}
		}
		return rows;
	}

	private void execute(String sql) throws SQLException {
		try (Connection connection = openDatabaseConnection(); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private Connection openDatabaseConnection() throws SQLException {
		return DriverManager.getConnection(databaseUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	/**
	 * Outside a Spring test context, {@code @TestConfiguration} classes on the test classpath would be picked up by
	 * component scanning (e.g. {@code TestcontainersConfiguration} would replace the datasource).
	 */
	static class ExcludeTestConfigurationsFilter extends TypeExcludeFilter {

		@Override
		public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
			return metadataReader.getAnnotationMetadata().isAnnotated(TestConfiguration.class.getName());
		}

	}

}
