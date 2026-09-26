package com.manuelaalecio.educare_backend.shared.security;

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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
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
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Startup scenarios of the requirements "Chave de assinatura obrigatória em produção" and "CORS restrito às origens
 * do frontend": the application is started with the {@code prod} profile, a real web server on a random port and all
 * other configuration valid. Same pattern as {@code AdminSeedScenarioTest}: its own container and a fresh database
 * per test instead of {@code TestcontainersConfiguration}, and the variables read by {@code application-prod.yaml}
 * passed as command-line arguments on a copy of the JVM environment without them.
 */
@Testcontainers
class SecurityStartupScenarioTest {

	private static final String JWT_SECRET_VARIABLE = "EDUCARE_JWT_SECRET";
	private static final String CORS_ALLOWED_ORIGINS_VARIABLE = "EDUCARE_CORS_ALLOWED_ORIGINS";
	private static final String ADMIN_PASSWORD_VARIABLE = "EDUCARE_ADMIN_PASSWORD";

	private static final String VALID_SECRET = "a".repeat(32);
	private static final String VALID_ORIGINS = "https://educare.example.org";

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

	private String databaseUrl;

	@BeforeEach
	void createFreshDatabase() throws SQLException {
		String databaseName = "security_startup_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + databaseName);
		}
		databaseUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + databaseName);
	}

	@Test
	@DisplayName("Scenario: Produção sem chave")
	void shouldRefuseStartupWhenProdProfileHasNoJwtSecret() {
		// given: the prod profile with database, admin password and CORS origins, and no signing key

		// when / then
		assertThatThrownBy(() -> startProdApplication(argument(CORS_ALLOWED_ORIGINS_VARIABLE, VALID_ORIGINS)))
			.hasStackTraceContaining(JWT_SECRET_VARIABLE);
	}

	@Test
	@DisplayName("Scenario: Chave curta demais")
	void shouldRefuseStartupWhenJwtSecretHasLessThan32Bytes() {
		// given
		String secret = "a".repeat(31);
		assertThat(secret.getBytes(StandardCharsets.UTF_8)).hasSize(31);

		// when / then
		assertThatThrownBy(() -> startProdApplication(argument(JWT_SECRET_VARIABLE, secret),
				argument(CORS_ALLOWED_ORIGINS_VARIABLE, VALID_ORIGINS)))
			.hasStackTraceContaining("educare.security.jwt.secret");
	}

	@Test
	@DisplayName("Scenario: Chave válida")
	void shouldStartAndAnswerHealthWhenJwtSecretHas32Bytes() throws IOException, InterruptedException {
		// given
		assertThat(VALID_SECRET.getBytes(StandardCharsets.UTF_8)).hasSize(32);

		// when
		try (ConfigurableApplicationContext context = startProdApplication(argument(JWT_SECRET_VARIABLE, VALID_SECRET),
				argument(CORS_ALLOWED_ORIGINS_VARIABLE, VALID_ORIGINS))) {
			// then
			assertThat(context.isRunning()).isTrue();
			int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
			try (HttpClient client = HttpClient.newHttpClient()) {
				HttpResponse<String> response = client.send(
						HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
						HttpResponse.BodyHandlers.ofString());
				assertThat(response.statusCode()).isEqualTo(200);
			}
		}
	}

	@Test
	@DisplayName("Scenario: Produção sem origens")
	void shouldRefuseStartupWhenProdProfileHasNoCorsOrigins() {
		// given: the prod profile with database, admin password and signing key, and no CORS origins

		// when / then
		assertThatThrownBy(() -> startProdApplication(argument(JWT_SECRET_VARIABLE, VALID_SECRET)))
			.hasStackTraceContaining(CORS_ALLOWED_ORIGINS_VARIABLE);
	}

	@Test
	@DisplayName("Scenario: Origem com curinga recusada")
	void shouldRefuseStartupWhenCorsOriginIsWildcard() {
		// given: the prod profile with all other configuration valid

		// when / then
		assertThatThrownBy(() -> startProdApplication(argument(JWT_SECRET_VARIABLE, VALID_SECRET),
				argument(CORS_ALLOWED_ORIGINS_VARIABLE, "*")))
			.hasStackTraceContaining("educare.security.cors.allowed-origins");
	}

	private ConfigurableApplicationContext startProdApplication(String... securityArguments) {
		String[] arguments = Stream.concat(Stream.of(
				// command-line arguments, so they take precedence over application-prod.yaml
				"--spring.profiles.active=prod",
				"--spring.datasource.url=" + databaseUrl,
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(),
				argument(ADMIN_PASSWORD_VARIABLE, "senhaAdmin123"),
				"--server.port=0",
				"--spring.devtools.restart.enabled=false"), Stream.of(securityArguments))
			.toArray(String[]::new);
		return new SpringApplicationBuilder(EducareBackendApplication.class)
			.web(WebApplicationType.SERVLET)
			.environment(environmentWithoutSecurityVariables())
			.initializers(context -> context.getBeanFactory()
				.registerSingleton(ExcludeTestComponentsFilter.class.getName(), new ExcludeTestComponentsFilter()))
			.run(arguments);
	}

	private static String argument(String name, String value) {
		return "--" + name + "=" + value;
	}

	/**
	 * The JVM environment cannot be changed, so the application gets a copy of it without the variables of these
	 * scenarios. This keeps them deterministic even when the variables are set where the tests run.
	 */
	private static StandardEnvironment environmentWithoutSecurityVariables() {
		StandardEnvironment environment = new StandardEnvironment();
		Map<String, Object> variables = new HashMap<>(environment.getSystemEnvironment());
		variables.remove(JWT_SECRET_VARIABLE);
		variables.remove(CORS_ALLOWED_ORIGINS_VARIABLE);
		variables.remove(ADMIN_PASSWORD_VARIABLE);
		environment.getPropertySources()
			.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
		return environment;
	}

	/**
	 * Outside a Spring test context, {@code @TestComponent} classes on the test classpath (including
	 * {@code @TestConfiguration} ones) would be picked up by component scanning: {@code TestcontainersConfiguration}
	 * would replace the datasource, and {@code SecurityTestController} would map the login and the health.
	 */
	static class ExcludeTestComponentsFilter extends TypeExcludeFilter {

		@Override
		public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
			return metadataReader.getAnnotationMetadata().isAnnotated(TestComponent.class.getName());
		}

	}

}
