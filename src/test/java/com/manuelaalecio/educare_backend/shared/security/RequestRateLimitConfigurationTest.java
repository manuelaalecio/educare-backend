package com.manuelaalecio.educare_backend.shared.security;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.error.GlobalExceptionHandler;
import com.manuelaalecio.educare_backend.shared.testsupport.AccessTokens;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The rate limit wired into MVC, with a limit of 2 requests per minute and the clock stopped: each test uses users
 * of its own, so the counts kept by the limiter do not cross tests.
 */
@WebMvcTest(SecurityTestController.class)
@Import({SecurityTestController.class, SecurityConfiguration.class, JwtConfiguration.class,
		MutableClockConfiguration.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
		AccessTokens.class})
@TestPropertySource(properties = "educare.rate-limit.max-requests=2")
class RequestRateLimitConfigurationTest {

	private static final String PROTECTED_PATH = "/api/v1/test-resources/me";
	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccessTokens accessTokens;

	@MockitoBean
	private AuthenticatedUserLookup lookup;

	@MockitoSpyBean
	private SecurityTestController controller;

	@Test
	void shouldRespondTooManyRequestsWhenUserExceedsLimit() throws Exception {
		// given
		String bearer = bearerOf(UUID.randomUUID(), "EDUCADOR");
		performTimes(2, PROTECTED_PATH, bearer);

		// when / then
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.detail").value("Muitas requisições. Aguarde um instante."))
			.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void shouldNotRunHandlerWhenRequestIsRefused() throws Exception {
		// given
		String bearer = bearerOf(UUID.randomUUID(), "EDUCADOR");
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/test-resources").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isCreated());
		}

		// when
		mockMvc.perform(post("/api/v1/test-resources").header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isTooManyRequests());

		// then
		verify(controller, times(2)).create();
	}

	@Test
	void shouldRespondUnauthorizedWhenTokenIsMissingEvenIfUserIsBlocked() throws Exception {
		// given
		String bearer = bearerOf(UUID.randomUUID(), "EDUCADOR");
		performTimes(2, PROTECTED_PATH, bearer);

		// when / then
		mockMvc.perform(get(PROTECTED_PATH)).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldCountRequestsRefusedByRoleWhenRouteRequiresRole() throws Exception {
		// given
		String bearer = bearerOf(UUID.randomUUID(), "EDUCADOR");
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(get("/api/v1/test-resources/admin").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isForbidden());
		}

		// when / then
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void shouldNotLimitPublicRoutesWhenRequestHasToken() throws Exception {
		// given
		String bearer = bearerOf(UUID.randomUUID(), "EDUCADOR");
		performTimes(2, PROTECTED_PATH, bearer);

		// when / then
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk());
			mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk());
		}
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void shouldNotAffectOtherUserWhenOneIsBlocked() throws Exception {
		// given
		String ana = bearerOf(UUID.randomUUID(), "EDUCADOR");
		String bruno = bearerOf(UUID.randomUUID(), "EDUCADOR");
		performTimes(2, PROTECTED_PATH, ana);

		// when / then
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, ana))
			.andExpect(status().isTooManyRequests());
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, bruno)).andExpect(status().isOk());
	}

	private void performTimes(int times, String path, String bearer) throws Exception {
		for (int i = 0; i < times; i++) {
			mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
		}
	}

	private String bearerOf(UUID id, String role) {
		given(lookup.findById(id)).willReturn(Optional.of(new TokenOwner(new AuthenticatedUser(id, role), null)));
		return accessTokens.bearer(id);
	}

}
