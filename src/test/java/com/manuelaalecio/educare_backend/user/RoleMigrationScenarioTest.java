package com.manuelaalecio.educare_backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import com.manuelaalecio.educare_backend.EducareBackendApplication;
import com.manuelaalecio.educare_backend.shared.security.AccessTokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Scenarios of the requirement "Migração das roles existentes" (spec user). They need the database stopped at V6, with
 * users in the former roles, before the application starts and applies V7, so they use their own container and a
 * fresh database per test instead of {@code TestcontainersConfiguration}, like {@link AdminSeedScenarioTest}.
 */
@Testcontainers
class RoleMigrationScenarioTest {

	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String JWT_SECRET = "a".repeat(32);
	private static final String USER_COLUMNS = "name || ':' || login || ':' || password_hash || ':' || created_at "
			+ "|| ':' || updated_at";

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

	private String databaseUrl;

	@BeforeEach
	void createDatabaseAtV6WithFormerRoles() throws SQLException {
		String databaseName = "role_migration_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + databaseName);
		}
		databaseUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + databaseName);

		Flyway.configure()
			.dataSource(databaseUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
			.locations("classpath:db/migration")
			.placeholders(Map.of("admin_password", "senhaAdmin123"))
			.target("6")
			.load()
			.migrate();
		// the admin seeded by V3 is ADMIN; Ana is created as USER, the former default
		execute("INSERT INTO users (id, name, login, password_hash, role, created_at, updated_at) VALUES ("
				+ "gen_random_uuid(), 'Ana Souza', '" + ANA_LOGIN + "', 'hash', 'USER', now(), now())");
	}

	@Test
	@DisplayName("Scenario: Roles antigas convertidas")
	void shouldConvertFormerRolesKeepingTheOtherColumnsWhenV7IsApplied() throws SQLException {
		// given
		assertThat(roleOf(ADMIN_LOGIN)).isEqualTo("ADMIN");
		assertThat(roleOf(ANA_LOGIN)).isEqualTo("USER");
		List<String> columnsBefore = query("SELECT " + USER_COLUMNS + " FROM users ORDER BY login");

		// when
		try (ConfigurableApplicationContext context = startApplication()) {
			// then
			assertThat(roleOf(ADMIN_LOGIN)).isEqualTo("COORDENADOR");
			assertThat(roleOf(ANA_LOGIN)).isEqualTo("EDUCADOR");
			assertThat(query("SELECT " + USER_COLUMNS + " FROM users ORDER BY login")).isEqualTo(columnsBefore);
		}
	}

	@Test
	@DisplayName("Scenario: Token anterior à conversão continua valendo")
	void shouldAcceptTokenIssuedBeforeConversionWithTheConvertedRole() throws Exception {
		// given: a token of the ADMIN issued before V7, signed with the key the application will use
		UUID adminId = UUID.fromString(query("SELECT id::text FROM users WHERE login = '" + ADMIN_LOGIN + "'").getFirst());
		String token = issuerBeforeStartup().issue(adminId).value();

		// when
		try (ConfigurableApplicationContext context = startApplication()) {
			HttpResponse<String> response = get(context, "/api/v1/users", token);

			// then
			assertThat(response.statusCode()).isEqualTo(200);
			List<String> adminRoles = JsonPath.read(response.body(),
					"$.content[?(@.login == '" + ADMIN_LOGIN + "')].role");
			assertThat(adminRoles).containsExactly("COORDENADOR");
		}
	}

	@Test
	@DisplayName("Scenario: Valor antigo recusado pelo banco")
	void shouldRejectFormerRoleInTheDatabaseAfterConversion() throws SQLException {
		// given
		startApplication().close();

		// when / then
		assertThatThrownBy(() -> execute("INSERT INTO users (id, name, login, password_hash, role, created_at, "
				+ "updated_at) VALUES (gen_random_uuid(), 'Carla', 'carla@educare.org', 'hash', 'ADMIN', now(), now())"))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("users_role_check");
		assertThat(query("SELECT id::text FROM users WHERE login = 'carla@educare.org'")).isEmpty();
	}

	private static AccessTokenIssuer issuerBeforeStartup() {
		var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
				new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		return new AccessTokenIssuer(encoder, Duration.ofHours(8), Clock.systemUTC());
	}

	private ConfigurableApplicationContext startApplication() {
		return new SpringApplicationBuilder(EducareBackendApplication.class)
			.environment(environmentWithoutSecurityVariables())
			.initializers(context -> context.getBeanFactory()
				.registerSingleton(AdminSeedScenarioTest.ExcludeTestConfigurationsFilter.class.getName(),
						new AdminSeedScenarioTest.ExcludeTestConfigurationsFilter()))
			.run("--spring.datasource.url=" + databaseUrl,
					"--spring.datasource.username=" + POSTGRES.getUsername(),
					"--spring.datasource.password=" + POSTGRES.getPassword(),
					"--spring.devtools.restart.enabled=false",
					"--server.port=0",
					"--EDUCARE_ADMIN_PASSWORD=senhaAdmin123",
					"--EDUCARE_JWT_SECRET=" + JWT_SECRET);
	}

	/** Without the variables of the machine running the tests, so the JWT key is always {@link #JWT_SECRET}. */
	private static StandardEnvironment environmentWithoutSecurityVariables() {
		StandardEnvironment environment = new StandardEnvironment();
		Map<String, Object> variables = new HashMap<>(environment.getSystemEnvironment());
		variables.remove("EDUCARE_ADMIN_PASSWORD");
		variables.remove("EDUCARE_JWT_SECRET");
		variables.remove("EDUCARE_CORS_ALLOWED_ORIGINS");
		environment.getPropertySources()
			.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
		return environment;
	}

	private static HttpResponse<String> get(ConfigurableApplicationContext context, String path, String token)
			throws IOException, InterruptedException {
		int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private String roleOf(String login) throws SQLException {
		return query("SELECT role FROM users WHERE login = '" + login + "'").getFirst();
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

}
