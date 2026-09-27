package com.manuelaalecio.educare_backend.shared.persistence;

import com.manuelaalecio.educare_backend.EducareBackendApplication;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenarios that need to start the application more than once or against a tampered database.
 * Uses its own container and a fresh database per test instead of {@code TestcontainersConfiguration}
 * (see design D7 of the setup-database-migrations change).
 */
@Testcontainers
class PersistenceStartupScenarioTest {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

	private String databaseUrl;

	@BeforeEach
	void createFreshDatabase() throws SQLException {
		String databaseName = "startup_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + databaseName);
		}
		databaseUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + databaseName);
	}

	@Test
	@DisplayName("Scenario: Banco vazio recebe todas as migrations")
	void shouldApplyBaselineMigrationWhenDatabaseIsEmpty() throws SQLException {
		// given: a fresh, empty database

		// when
		try (ConfigurableApplicationContext context = startApplication()) {
			// then
			assertThat(context.isRunning()).isTrue();
			assertThat(query("SELECT version || ':' || success FROM flyway_schema_history "
					+ "WHERE version IS NOT NULL ORDER BY installed_rank"))
				.containsExactly("1:true", "2:true", "3:true", "4:true");
		}
	}

	@Test
	@DisplayName("Scenario: Reinicialização não reaplica migrations")
	void shouldKeepHistoryUnchangedWhenApplicationRestarts() throws SQLException {
		// given
		startApplication().close();
		List<String> historyBeforeRestart = history();

		// when
		try (ConfigurableApplicationContext context = startApplication()) {
			// then
			assertThat(context.isRunning()).isTrue();
			assertThat(history()).hasSameSizeAs(historyBeforeRestart).isEqualTo(historyBeforeRestart);
		}
	}

	@Test
	@DisplayName("Scenario: Migration aplicada alterada impede a inicialização")
	void shouldRefuseStartupWhenAppliedMigrationChecksumDiffers() throws SQLException {
		// given
		startApplication().close();
		execute("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
		List<String> historyBeforeStartup = history();

		// when / then
		assertThatThrownBy(this::startApplication)
			.satisfies(error -> assertThat(causeOfType(error, FlywayValidateException.class))
				.isNotNull()
				.hasMessageContaining("checksum mismatch")
				.hasMessageContaining("version 1"));
		assertThat(history()).isEqualTo(historyBeforeStartup);
	}

	@Test
	@DisplayName("Scenario: Entidade sem tabela correspondente impede a inicialização")
	void shouldRefuseStartupWhenEntityTableIsMissing() throws SQLException {
		// given
		startApplication().close();
		execute("DROP TABLE test_audited_entity");

		// when / then
		assertThatThrownBy(this::startApplication)
			.satisfies(error -> assertThat(causeOfType(error, SchemaManagementException.class))
				.isNotNull()
				.hasMessageContaining("test_audited_entity"));
		assertThat(query("SELECT table_name FROM information_schema.tables WHERE table_name = 'test_audited_entity'"))
			.isEmpty();
	}

	private ConfigurableApplicationContext startApplication() {
		return new SpringApplicationBuilder(EducareBackendApplication.class)
			.web(WebApplicationType.NONE)
			.initializers(context -> context.getBeanFactory()
				.registerSingleton(ExcludeTestConfigurationsFilter.class.getName(), new ExcludeTestConfigurationsFilter()))
			// command-line arguments, so they take precedence over application-dev.yaml
			.run("--spring.datasource.url=" + databaseUrl,
					"--spring.datasource.username=" + POSTGRES.getUsername(),
					"--spring.datasource.password=" + POSTGRES.getPassword(),
					"--spring.devtools.restart.enabled=false");
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

	private static Throwable causeOfType(Throwable error, Class<? extends Throwable> type) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return current;
			}
		}
		return null;
	}

	/**
	 * Outside a Spring test context, {@code @TestConfiguration} classes on the test classpath would be
	 * picked up by component scanning (e.g. {@code TestcontainersConfiguration} would replace the datasource).
	 */
	static class ExcludeTestConfigurationsFilter extends TypeExcludeFilter {

		@Override
		public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
			return metadataReader.getAnnotationMetadata().isAnnotated(TestConfiguration.class.getName());
		}

	}

}
