package com.manuelaalecio.educare_backend.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.error.GlobalExceptionHandler;
import com.manuelaalecio.educare_backend.shared.testsupport.AccessTokens;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(SecurityTestController.class)
@Import({SecurityTestController.class, SecurityConfiguration.class, JwtConfiguration.class,
		MutableClockConfiguration.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
		AccessTokens.class})
@TestPropertySource(properties = "educare.security.cors.allowed-origins=" + SecurityConfigurationTest.ALLOWED_ORIGIN)
class SecurityConfigurationTest {

	static final String ALLOWED_ORIGIN = "https://educare.example.org";

	private static final String OTHER_ORIGIN = "https://outro.example.com";
	private static final UUID ADMIN_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");
	private static final UUID MISSING_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000099");
	private static final String PROTECTED_PATH = "/api/v1/test-resources/me";
	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccessTokens accessTokens;

	@Autowired
	private MutableClock clock;

	@MockitoBean
	private AuthenticatedUserLookup lookup;

	// ---------- authentication ----------

	@Test
	void shouldRespondUnauthorizedWhenTokenIsMissing() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(PROTECTED_PATH));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldRespondUnauthorizedWhenSchemeIsBasic() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(PROTECTED_PATH)
				.header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW5AZWR1Y2FyZS5vcmc6ZWR1Y2FyZTEyMw=="));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldRespondUnauthorizedWhenTokenIsMalformed() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(PROTECTED_PATH)
				.header(HttpHeaders.AUTHORIZATION, "Bearer abc.def"));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldRespondUnauthorizedWhenTokenIsSignedWithAnotherKey() throws Exception {
		// given
		givenUser(ADMIN_ID, "ADMIN");

		// when
		ResultActions result = mockMvc.perform(get(PROTECTED_PATH)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.signedWithAnotherKey(ADMIN_ID)));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldRespondUnauthorizedWhenTokenIsExpired() throws Exception {
		// given
		givenUser(ADMIN_ID, "ADMIN");
		String authorization = accessTokens.bearer(ADMIN_ID);
		clock.setInstant(clock.instant().plus(Duration.ofHours(8).plusSeconds(1)));

		// when
		ResultActions result = mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, authorization));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldRespondUnauthorizedWhenUserOfTokenDoesNotExist() throws Exception {
		// given
		given(lookup.findById(MISSING_ID)).willReturn(Optional.empty());

		// when
		ResultActions result = mockMvc.perform(get(PROTECTED_PATH)
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(MISSING_ID)));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldAuthenticateUserOfTokenWhenTokenIsValid() throws Exception {
		// given
		givenUser(USER_ID, "USER");

		// when
		ResultActions result = mockMvc.perform(get(PROTECTED_PATH)
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(USER_ID)));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(USER_ID.toString()));
	}

	@Test
	void shouldRespondUnauthorizedWhenRouteDoesNotExistAndTokenIsMissing() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/api/v1/nao-existe"));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldRespondNotFoundWhenRouteDoesNotExistAndUserIsAuthenticated() throws Exception {
		// given
		givenUser(USER_ID, "USER");

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/nao-existe")
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(USER_ID)));

		// then
		result.andExpect(status().isNotFound());
	}

	// ---------- public routes ----------

	@Test
	void shouldAllowLoginWhenTokenIsMissing() throws Exception {
		// when
		ResultActions result = mockMvc.perform(post("/api/v1/auth/login"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("login"));
	}

	@Test
	void shouldRequireTokenWhenLoginPathIsCalledWithAnotherMethod() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/api/v1/auth/login"));

		// then
		assertUnauthorizedWithoutReason(result);
	}

	@Test
	void shouldAllowHealthWhenTokenIsMissing() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/actuator/health"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	// ---------- authorization ----------

	@Test
	void shouldRespondForbiddenProblemWhenRoleIsInsufficient() throws Exception {
		// given
		givenUser(USER_ID, "USER");

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/test-resources/admin")
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(USER_ID)));

		// then
		result.andExpect(status().isForbidden())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
				.andExpect(jsonPath("$.title").value("Forbidden"))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.detail").value("Acesso negado"))
				.andExpect(jsonPath("$.instance").value("/api/v1/test-resources/admin"));
	}

	@Test
	void shouldAllowAccessWhenRoleIsSufficient() throws Exception {
		// given
		givenUser(ADMIN_ID, "ADMIN");

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/test-resources/admin")
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(ADMIN_ID)));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("admin"));
	}

	// ---------- CORS ----------

	@Test
	void shouldAllowPreflightWhenOriginIsAllowed() throws Exception {
		// when
		ResultActions result = mockMvc.perform(preflight(ALLOWED_ORIGIN));

		// then
		result.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						containsStringIgnoringCase("authorization")))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void shouldRejectPreflightWhenOriginIsNotAllowed() throws Exception {
		// when
		ResultActions result = mockMvc.perform(preflight(OTHER_ORIGIN));

		// then
		result.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void shouldExposeLocationHeaderWhenCorsRequestCreatesResource() throws Exception {
		// given
		givenUser(ADMIN_ID, "ADMIN");

		// when
		ResultActions result = mockMvc.perform(post("/api/v1/test-resources")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(ADMIN_ID)));

		// then
		result.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, SecurityTestController.CREATED_LOCATION))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")));
	}

	private void givenUser(UUID id, String role) {
		given(lookup.findById(id)).willReturn(Optional.of(new AuthenticatedUser(id, role)));
	}

	private static MockHttpServletRequestBuilder preflight(String origin) {
		return options("/api/v1/users")
				.header(HttpHeaders.ORIGIN, origin)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization");
	}

	/** The refusal is a generic 401, which does not tell whether the token was missing, malformed, expired etc. */
	private static void assertUnauthorizedWithoutReason(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.detail").value("Autenticação necessária"))
				.andExpect(content().string(not(containsStringIgnoringCase("expired"))))
				.andExpect(content().string(not(containsStringIgnoringCase("signature"))))
				.andExpect(content().string(not(containsStringIgnoringCase("invalid_token"))))
				.andExpect(content().string(not(containsStringIgnoringCase("error_description"))));
	}

}
