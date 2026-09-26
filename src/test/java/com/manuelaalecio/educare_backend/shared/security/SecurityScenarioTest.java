package com.manuelaalecio.educare_backend.shared.security;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.testsupport.AccessTokens;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Scenarios of the {@code security} spec that run with the application up. The startup scenarios in {@code prod}
 * are in {@link SecurityStartupScenarioTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class, AccessTokens.class})
@TestPropertySource(properties = "educare.security.cors.allowed-origins=" + SecurityScenarioTest.ALLOWED_ORIGIN)
class SecurityScenarioTest {

	static final String ALLOWED_ORIGIN = "https://educare.example.org";

	private static final String OTHER_ORIGIN = "https://outro.example.com";
	private static final String USERS = "/api/v1/users";
	private static final String ME = "/api/v1/auth/me";
	private static final String LOGIN = "/api/v1/auth/login";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String UNAUTHORIZED_DETAIL = "Autenticação necessária";
	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String ADMIN_PASSWORD = "educare123";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String ANA_PASSWORD = "segredo123";
	private static final String BRUNO_LOGIN = "bruno.lima@educare.org";
	private static final String BRUNO_PASSWORD = "segredo456";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private AccessTokens accessTokens;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM users");
		clock.setInstant(NOW);
	}

	// ---------- Autenticação obrigatória fora das rotas públicas ----------

	@Test
	@DisplayName("Scenario: Rota protegida sem token")
	void shouldRespondUnauthorizedWhenProtectedRouteHasNoToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(USERS));

		// then
		expectUnauthorized(result);
	}

	@Test
	@DisplayName("Scenario: Esquema de autenticação diferente de Bearer")
	void shouldRespondUnauthorizedWhenSchemeIsNotBearer() throws Exception {
		// given
		testUsers.create("Administrador", ADMIN_LOGIN, ADMIN_PASSWORD, "ADMIN");

		// when
		ResultActions result = mockMvc.perform(get(ME)
				.header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW5AZWR1Y2FyZS5vcmc6ZWR1Y2FyZTEyMw=="));

		// then
		expectUnauthorized(result);
	}

	@Test
	@DisplayName("Scenario: Rota inexistente sem token")
	void shouldRespondUnauthorizedWhenUnknownRouteHasNoToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/api/v1/nao-existe"));

		// then: same response as an existing protected route, so it does not reveal whether the route exists
		expectUnauthorized(result);
	}

	@Test
	@DisplayName("Scenario: Health público")
	void shouldRespondOkWhenHealthHasNoToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/actuator/health"));

		// then
		result.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Scenario: Login público")
	void shouldRespondOkWhenLoginHasCorrectCredentialsAndNoToken() throws Exception {
		// given
		testUsers.create("Ana Souza", ANA_LOGIN, ANA_PASSWORD, "USER");

		// when
		ResultActions result = mockMvc.perform(post(LOGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"login": "ana.souza@educare.org", "password": "segredo123"}"""));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	// ---------- Token recusado quando inválido ou de usuário inexistente ----------

	@Test
	@DisplayName("Scenario: Token malformado")
	void shouldRespondUnauthorizedWhenTokenIsMalformed() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer abc.def"));

		// then
		expectUnauthorized(result);
	}

	@Test
	@DisplayName("Scenario: Token assinado com outra chave")
	void shouldRespondUnauthorizedWhenTokenIsSignedWithAnotherKey() throws Exception {
		// given: an existing user and a token for her id, within its validity, signed with another key
		UUID ana = testUsers.create("Ana Souza", ANA_LOGIN, ANA_PASSWORD, "USER");
		String token = accessTokens.signedWithAnotherKey(ana);

		// when
		ResultActions result = mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

		// then
		expectUnauthorized(result);
	}

	@Test
	@DisplayName("Scenario: Token de usuário excluído")
	void shouldRespondUnauthorizedWhenTokenUserWasDeleted() throws Exception {
		// given
		testUsers.create("Administrador", ADMIN_LOGIN, ADMIN_PASSWORD, "ADMIN");
		UUID ana = testUsers.create("Ana Souza", ANA_LOGIN, ANA_PASSWORD, "USER");
		String anaBearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, ADMIN_PASSWORD);
		mockMvc.perform(delete(USERS + "/" + ana).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isNoContent());

		// when
		ResultActions result = mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, anaBearer));

		// then
		expectUnauthorized(result);
	}

	@Test
	@DisplayName("Scenario: Token de usuário existente")
	void shouldRespondOkWhenTokenUserExists() throws Exception {
		// given
		testUsers.create("Ana Souza", ANA_LOGIN, ANA_PASSWORD, "USER");
		String anaBearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// when
		ResultActions result = mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, anaBearer));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.login").value(ANA_LOGIN));
	}

	// ---------- Role vigente decide o acesso ----------

	@Test
	@DisplayName("Scenario: USER em rota de ADMIN")
	void shouldRespondForbiddenWhenUserCallsAdminRoute() throws Exception {
		// given
		testUsers.create("Ana Souza", ANA_LOGIN, ANA_PASSWORD, "USER");
		String anaBearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// when
		ResultActions result = mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, anaBearer));

		// then
		expectForbidden(result);
	}

	@Test
	@DisplayName("Scenario: Rebaixamento vale no mesmo token")
	void shouldRespondForbiddenWithSameTokenWhenAdminIsDemoted() throws Exception {
		// given
		testUsers.create("Administrador", ADMIN_LOGIN, ADMIN_PASSWORD, "ADMIN");
		UUID bruno = testUsers.create("Bruno Lima", BRUNO_LOGIN, BRUNO_PASSWORD, "ADMIN");
		String brunoBearer = testUsers.bearer(BRUNO_LOGIN, BRUNO_PASSWORD);
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, ADMIN_PASSWORD);
		changeRole(adminBearer, bruno, "Bruno Lima", BRUNO_LOGIN, "USER");

		// when
		ResultActions result = mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, brunoBearer));

		// then
		expectForbidden(result);
	}

	@Test
	@DisplayName("Scenario: Promoção vale no mesmo token")
	void shouldRespondOkWithSameTokenWhenUserIsPromoted() throws Exception {
		// given
		testUsers.create("Administrador", ADMIN_LOGIN, ADMIN_PASSWORD, "ADMIN");
		UUID ana = testUsers.create("Ana Souza", ANA_LOGIN, ANA_PASSWORD, "USER");
		String anaBearer = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, ADMIN_PASSWORD);
		changeRole(adminBearer, ana, "Ana Souza", ANA_LOGIN, "ADMIN");

		// when
		ResultActions result = mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, anaBearer));

		// then
		result.andExpect(status().isOk());
	}

	// ---------- CORS restrito às origens do frontend ----------

	@Test
	@DisplayName("Scenario: Preflight de origem permitida")
	void shouldAllowPreflightWhenOriginIsConfigured() throws Exception {
		// when
		ResultActions result = mockMvc.perform(preflight(ALLOWED_ORIGIN));

		// then
		result.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						containsStringIgnoringCase("authorization")));
	}

	@Test
	@DisplayName("Scenario: Preflight de origem não permitida")
	void shouldRejectPreflightWhenOriginIsNotConfigured() throws Exception {
		// when
		ResultActions result = mockMvc.perform(preflight(OTHER_ORIGIN));

		// then
		result.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("Scenario: Header Location exposto")
	void shouldExposeLocationHeaderWhenAdminCreatesUserFromAllowedOrigin() throws Exception {
		// given
		testUsers.create("Administrador", ADMIN_LOGIN, ADMIN_PASSWORD, "ADMIN");
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, ADMIN_PASSWORD);

		// when
		ResultActions result = mockMvc.perform(post(USERS)
				.header(HttpHeaders.AUTHORIZATION, adminBearer)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123"}"""));

		// then
		result.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
						containsStringIgnoringCase("Location")));
	}

	// ---------- helpers ----------

	private void expectUnauthorized(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.detail").value(UNAUTHORIZED_DETAIL));
	}

	private void expectForbidden(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));
	}

	private void changeRole(String adminBearer, UUID id, String name, String login, String role) throws Exception {
		mockMvc.perform(put(USERS + "/" + id)
				.header(HttpHeaders.AUTHORIZATION, adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "login": "%s", "role": "%s"}""".formatted(name, login, role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value(role));
	}

	private static MockHttpServletRequestBuilder preflight(String origin) {
		return options(USERS)
				.header(HttpHeaders.ORIGIN, origin)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization");
	}

}
