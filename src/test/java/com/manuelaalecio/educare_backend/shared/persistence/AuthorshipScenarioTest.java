package com.manuelaalecio.educare_backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.manuelaalecio.educare_backend.EducareBackendApplication;
import com.manuelaalecio.educare_backend.shared.testsupport.AuthenticatedAs;
import com.manuelaalecio.educare_backend.shared.testsupport.TestUsers;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.persistence.TestAuditedEntity;
import com.manuelaalecio.educare_backend.shared.testsupport.persistence.TestAuditedEntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Scenarios of the requirement "Autoria dos registros". Records are written through the real API with real tokens,
 * and the authorship is read straight from the database, since the API does not expose it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class AuthorshipScenarioTest {

	private static final String USERS = "/api/v1/users";
	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String BRUNO_LOGIN = "bruno.lima@educare.org";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String PASSWORD = "segredo123";
	private static final String NEW_ANA_BODY = """
			{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123", "role": "USER"}""";

	/** Property names that would carry the authorship in a response. */
	private static final Pattern AUTHORSHIP_PROPERTY = Pattern.compile("(?i).*(created_?by|updated_?by|author).*");
	private static final Pattern JSON_PROPERTY = Pattern.compile("\"([^\"]+)\"\\s*:");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private TestAuditedEntityRepository testAuditedEntityRepository;

	@Autowired
	private PostgreSQLContainer postgres;

	private UUID adminId;
	private UUID brunoId;
	private String adminBearer;
	private String brunoBearer;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("DELETE FROM users");
		adminId = testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "ADMIN");
		brunoId = testUsers.create("Bruno Lima", BRUNO_LOGIN, PASSWORD, "ADMIN");
		adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);
		brunoBearer = testUsers.bearer(BRUNO_LOGIN, PASSWORD);
	}

	@AfterEach
	void cleanUp() {
		testAuditedEntityRepository.deleteAll();
	}

	@Test
	@DisplayName("Scenario: Criação registra o usuário autenticado")
	void shouldRecordAuthenticatedUserAsBothAuthorsWhenRecordIsCreated() throws Exception {
		// when
		String response = perform(adminBearer, json(post(USERS), NEW_ANA_BODY))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		// then
		UUID anaId = idOf(response);
		assertThat(createdBy(anaId)).isEqualTo(adminId);
		assertThat(updatedBy(anaId)).isEqualTo(adminId);
	}

	@Test
	@DisplayName("Scenario: Alteração muda só o autor da última alteração")
	void shouldChangeOnlyLastModifiedAuthorWhenAnotherUserChangesRecord() throws Exception {
		// given
		UUID anaId = createAnaAs(adminBearer);

		// when
		perform(brunoBearer, json(put(USERS + "/{id}", anaId), """
				{"name": "Ana Souza Lima", "login": "ana.souza@educare.org", "role": "USER"}"""))
				.andExpect(status().isOk());

		// then
		assertThat(createdBy(anaId)).isEqualTo(adminId);
		assertThat(updatedBy(anaId)).isEqualTo(brunoId);
	}

	@Test
	@DisplayName("Scenario: Usuário que altera o próprio registro")
	void shouldRecordUserAsLastModifiedAuthorWhenUserChangesOwnPassword() throws Exception {
		// given
		UUID anaId = createAnaAs(adminBearer);
		String anaBearer = testUsers.bearer(ANA_LOGIN, PASSWORD);

		// when
		perform(anaBearer, json(put("/api/v1/auth/me/password"), """
				{"currentPassword": "segredo123", "newPassword": "novaSenha456"}"""))
				.andExpect(status().isNoContent());

		// then
		assertThat(createdBy(anaId)).isEqualTo(adminId);
		assertThat(updatedBy(anaId)).isEqualTo(anaId);
	}

	@Test
	@DisplayName("Scenario: Requisição recusada não muda a autoria")
	void shouldKeepAuthorshipWhenChangeIsRejected() throws Exception {
		// given
		UUID anaId = createAnaAs(adminBearer);

		// when
		perform(brunoBearer, json(put(USERS + "/{id}", anaId), """
				{"name": "", "login": "ana", "role": "USER"}"""))
				.andExpect(status().isBadRequest());

		// then
		assertThat(createdBy(anaId)).isEqualTo(adminId);
		assertThat(updatedBy(anaId)).isEqualTo(adminId);
	}

	/**
	 * No request can change the creation author of a user, so the record is saved as in a request of Bruno (with
	 * {@link AuthenticatedAs}) after the creation author is tampered with on the entity.
	 */
	@Test
	@DisplayName("Scenario: Tentativa de alterar o autor da criação é ignorada")
	void shouldKeepOriginalCreationAuthorWhenCreationAuthorIsTamperedBeforeSaving() {
		// given
		TestAuditedEntity saved = AuthenticatedAs.call(adminId,
				() -> testAuditedEntityRepository.saveAndFlush(new TestAuditedEntity("Ana")));
		ReflectionTestUtils.setField(saved, "createdBy", brunoId);
		saved.rename("Ana Maria");

		// when
		AuthenticatedAs.run(brunoId, () -> testAuditedEntityRepository.saveAndFlush(saved));

		// then
		assertThat(authorInDatabase("test_audited_entity", "created_by", saved.getId())).isEqualTo(adminId);
		assertThat(authorInDatabase("test_audited_entity", "updated_by", saved.getId())).isEqualTo(brunoId);
	}

	/**
	 * The shared test database may no longer have the seeded admin (other tests clean the {@code users} table), so the
	 * application is started against a new, empty database in the same container.
	 */
	@Test
	@DisplayName("Scenario: Registro gravado sem usuário autenticado")
	void shouldLeaveAuthorshipEmptyWhenAdminIsSeededByMigrations() throws SQLException {
		// given
		String databaseUrl = createEmptyDatabase();

		// when
		try (ConfigurableApplicationContext context = startApplication(databaseUrl)) {
			// then
			assertThat(context.isRunning()).isTrue();
			assertThat(query(databaseUrl, "SELECT login || ':' || (created_by IS NULL) || ':' || (updated_by IS NULL) "
					+ "FROM users"))
				.containsExactly(ADMIN_LOGIN + ":true:true");
		}
	}

	@Test
	@DisplayName("Scenario: Autor excluído mantém a autoria")
	void shouldKeepCreationAuthorWhenAuthorIsDeleted() throws Exception {
		// given
		UUID anaId = createAnaAs(brunoBearer);

		// when
		perform(adminBearer, delete(USERS + "/{id}", brunoId)).andExpect(status().isNoContent());

		// then
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, brunoId))
			.isZero();
		assertThat(createdBy(anaId)).isEqualTo(brunoId);
	}

	@Test
	@DisplayName("Scenario: Respostas não expõem a autoria")
	void shouldNotExposeAuthorshipWhenRecordIsReturned() throws Exception {
		// when
		String created = perform(adminBearer, json(post(USERS), NEW_ANA_BODY))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		UUID anaId = idOf(created);
		String found = perform(adminBearer, get(USERS + "/{id}", anaId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String listed = perform(adminBearer, get(USERS))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// then
		assertThat(createdBy(anaId)).isEqualTo(adminId);
		assertThat(List.of(created, found, listed)).allSatisfy(body -> {
			assertThat(propertyNames(body)).isNotEmpty().noneMatch(name -> AUTHORSHIP_PROPERTY.matcher(name).matches());
		});
	}

	private UUID createAnaAs(String bearer) throws Exception {
		String response = perform(bearer, json(post(USERS), NEW_ANA_BODY))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idOf(response);
	}

	private ResultActions perform(String bearer,
			MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static UUID idOf(String response) {
		return UUID.fromString(JsonPath.read(response, "$.id"));
	}

	private static List<String> propertyNames(String json) {
		List<String> names = new ArrayList<>();
		Matcher matcher = JSON_PROPERTY.matcher(json);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	private UUID createdBy(UUID userId) {
		return authorInDatabase("users", "created_by", userId);
	}

	private UUID updatedBy(UUID userId) {
		return authorInDatabase("users", "updated_by", userId);
	}

	private UUID authorInDatabase(String table, String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", UUID.class, id);
	}

	private String createEmptyDatabase() throws SQLException {
		String databaseName = "authorship_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + databaseName);
		}
		return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + databaseName);
	}

	private ConfigurableApplicationContext startApplication(String databaseUrl) {
		return new SpringApplicationBuilder(EducareBackendApplication.class)
			.web(WebApplicationType.NONE)
			.initializers(context -> context.getBeanFactory()
				.registerSingleton(ExcludeTestConfigurationsFilter.class.getName(), new ExcludeTestConfigurationsFilter()))
			// command-line arguments, so they take precedence over application-dev.yaml
			.run("--spring.datasource.url=" + databaseUrl,
					"--spring.datasource.username=" + postgres.getUsername(),
					"--spring.datasource.password=" + postgres.getPassword(),
					"--spring.devtools.restart.enabled=false");
	}

	private List<String> query(String databaseUrl, String sql) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection connection = DriverManager.getConnection(databaseUrl, postgres.getUsername(),
				postgres.getPassword());
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				rows.add(resultSet.getString(1));
			}
		}
		return rows;
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
