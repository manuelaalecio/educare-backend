package com.manuelaalecio.educare_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.TestUsers;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class})
class AuthScenarioTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String ME = "/api/v1/auth/me";
	private static final String ME_PASSWORD = "/api/v1/auth/me/password";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final Instant ISSUED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final String ANA_NAME = "Ana Souza";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String ANA_PASSWORD = "segredo123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@Autowired
	private TestUsers testUsers;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM users");
		clock.setInstant(ISSUED_AT);
	}

	// ---------- Login com email e senha ----------

	@Test
	@DisplayName("Scenario: Login com credenciais corretas")
	void shouldIssueTokenWhenCredentialsAreCorrect() throws Exception {
		// given
		createAna();

		// when
		ResultActions result = login("""
				{"login": "ana.souza@educare.org", "password": "segredo123"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(28800))
				.andReturn();
		String accessToken = accessTokenOf(mvcResult);
		assertThat(accessToken).isNotBlank();
		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.login").value(ANA_LOGIN));
	}

	@Test
	@DisplayName("Scenario: Login sem diferenciar maiúsculas")
	void shouldIssueTokenWhenLoginDiffersOnlyInCase() throws Exception {
		// given
		createAna();

		// when
		ResultActions result = login("""
				{"login": "Ana.Souza@EDUCARE.org", "password": "segredo123"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isOk()).andReturn();
		assertThat(accessTokenOf(mvcResult)).isNotBlank();
	}

	@Test
	@DisplayName("Scenario: Senha incorreta")
	void shouldRejectLoginWhenPasswordIsWrong() throws Exception {
		// given
		createAna();

		// when
		ResultActions result = login("""
				{"login": "ana.souza@educare.org", "password": "errada123"}""");

		// then
		String body = result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.accessToken").doesNotExist())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(body).doesNotContain(ANA_LOGIN);
	}

	@Test
	@DisplayName("Scenario: Email não cadastrado responde igual à senha incorreta")
	void shouldRejectUnknownLoginLikeWrongPasswordWhenLoginDoesNotExist() throws Exception {
		// given
		createAna();
		String wrongPasswordBody = login("""
				{"login": "ana.souza@educare.org", "password": "errada123"}""")
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		// when
		ResultActions result = login("""
				{"login": "ninguem@educare.org", "password": "segredo123"}""");

		// then
		String body = result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.accessToken").doesNotExist())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat((String) JsonPath.read(body, "$.title"))
				.isNotBlank()
				.isEqualTo(JsonPath.read(wrongPasswordBody, "$.title"));
		assertThat((String) JsonPath.read(body, "$.detail"))
				.isNotBlank()
				.isEqualTo(JsonPath.read(wrongPasswordBody, "$.detail"));
		assertThat(body).doesNotContain("ninguem@educare.org");
	}

	@Test
	@DisplayName("Scenario: Campos ausentes")
	void shouldRejectLoginWhenFieldsAreMissing() throws Exception {
		// when
		ResultActions result = login("{}");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("login")))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
	}

	@Test
	@DisplayName("Scenario: Senha acima de 72 bytes")
	void shouldRejectLoginWhenPasswordExceeds72Bytes() throws Exception {
		// given
		String password = "ç".repeat(40);
		assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(80);

		// when
		ResultActions result = login("""
				{"login": "ana.souza@educare.org", "password": "%s"}""".formatted(password));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
	}

	@Test
	@DisplayName("Scenario: JSON inválido no login")
	void shouldRejectLoginWhenBodyIsNotValidJson() throws Exception {
		// when
		ResultActions result = login("{\"login\": \"ana");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	// ---------- Access token com validade de 8 horas e sem dados pessoais ----------

	@Test
	@DisplayName("Scenario: Token aceito dentro da validade")
	void shouldAcceptTokenWhenWithinValidity() throws Exception {
		// given
		createAna();
		clock.setInstant(ISSUED_AT);
		String bearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// when
		clock.setInstant(Instant.parse("2026-03-01T17:59:00Z"));
		ResultActions result = mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer));

		// then
		result.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Scenario: Token expirado")
	void shouldRejectTokenWhenExpired() throws Exception {
		// given
		createAna();
		clock.setInstant(ISSUED_AT);
		String bearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// when
		clock.setInstant(Instant.parse("2026-03-01T18:01:00Z"));
		ResultActions result = mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer));

		// then
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Scenario: Token não expõe dados pessoais")
	void shouldNotExposePersonalDataWhenTokenIsDecoded() throws Exception {
		// given
		UUID anaId = createAna();

		// when
		String token = testUsers.login(ANA_LOGIN, ANA_PASSWORD);

		// then
		String[] parts = token.split("\\.");
		assertThat(parts).hasSize(3);
		String header = decodeBase64Url(parts[0]);
		String payload = decodeBase64Url(parts[1]);
		assertThat(header + payload).contains(anaId.toString());
		assertThat(header).doesNotContain(ANA_LOGIN, ANA_NAME, ANA_PASSWORD);
		assertThat(payload).doesNotContain(ANA_LOGIN, ANA_NAME, ANA_PASSWORD);
	}

	// ---------- Consultar os próprios dados ----------

	@Test
	@DisplayName("Scenario: USER consulta os próprios dados")
	void shouldReturnOwnDataWhenUserRequestsMe() throws Exception {
		// given
		UUID anaId = createAna();
		String bearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// when
		ResultActions result = mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(anaId.toString()))
				.andExpect(jsonPath("$.name").value(ANA_NAME))
				.andExpect(jsonPath("$.login").value(ANA_LOGIN))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	@DisplayName("Scenario: Consulta sem token")
	void shouldRejectMeWhenAuthorizationHeaderIsMissing() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(ME));

		// then
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	// ---------- Trocar a própria senha ----------

	@Test
	@DisplayName("Scenario: Senha trocada")
	void shouldChangePasswordWhenCurrentPasswordMatches() throws Exception {
		// given
		UUID anaId = createAna();
		String bearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);
		Instant changedAt = Instant.parse("2026-03-01T11:00:00Z");
		clock.setInstant(changedAt);

		// when
		ResultActions result = changePassword(bearer, """
				{"currentPassword": "segredo123", "newPassword": "novaSenha456"}""");

		// then
		result.andExpect(status().isNoContent())
				.andExpect(content().string(""));
		loginAna("novaSenha456").andExpect(status().isOk());
		loginAna(ANA_PASSWORD).andExpect(status().isUnauthorized());
		assertThat(updatedAtInDatabase(anaId)).isEqualTo(changedAt);
	}

	@Test
	@DisplayName("Scenario: Senha atual incorreta")
	void shouldRejectPasswordChangeWhenCurrentPasswordIsWrong() throws Exception {
		// given
		createAna();
		String bearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// when
		ResultActions result = changePassword(bearer, """
				{"currentPassword": "errada123", "newPassword": "novaSenha456"}""");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("currentPassword")));
		loginAna(ANA_PASSWORD).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Scenario: Nova senha inválida")
	void shouldRejectPasswordChangeWhenNewPasswordIsInvalid() throws Exception {
		// given
		createAna();
		String bearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// when
		ResultActions result = changePassword(bearer, """
				{"currentPassword": "segredo123", "newPassword": "curta"}""");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("newPassword")))
				.andExpect(jsonPath("$.errors[*].field").value(not(hasItem("currentPassword"))));
		loginAna(ANA_PASSWORD).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Scenario: Troca sem token")
	void shouldRejectPasswordChangeWhenAuthorizationHeaderIsMissing() throws Exception {
		// given
		UUID anaId = createAna();
		String hashBefore = passwordHashInDatabase(anaId);

		// when
		ResultActions result = mockMvc.perform(put(ME_PASSWORD)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword": "segredo123", "newPassword": "novaSenha456"}"""));

		// then
		result.andExpect(status().isUnauthorized());
		assertThat(passwordHashInDatabase(anaId)).isEqualTo(hashBefore);
		loginAna(ANA_PASSWORD).andExpect(status().isOk());
	}

	// ---------- helpers ----------

	private UUID createAna() {
		return testUsers.create(ANA_NAME, ANA_LOGIN, ANA_PASSWORD, "USER");
	}

	private ResultActions login(String body) throws Exception {
		return mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions loginAna(String password) throws Exception {
		return login("""
				{"login": "%s", "password": "%s"}""".formatted(ANA_LOGIN, password));
	}

	private ResultActions changePassword(String bearer, String body) throws Exception {
		return mockMvc.perform(put(ME_PASSWORD)
				.header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static String accessTokenOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
	}

	private static String decodeBase64Url(String part) {
		return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
	}

	private String passwordHashInDatabase(UUID id) {
		return jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
	}

	private Instant updatedAtInDatabase(UUID id) {
		return jdbcTemplate.queryForObject("SELECT updated_at FROM users WHERE id = ?", Timestamp.class, id)
				.toInstant();
	}

}
