package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ProblemDetailSecurityHandlersTest {

	// the same customization that Spring Boot applies to the context JsonMapper
	private final JsonMapper jsonMapper = JsonMapper.builder()
			.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
			.build();

	private final ProblemDetailSecurityHandlers handlers = new ProblemDetailSecurityHandlers(jsonMapper);

	@Test
	void shouldRespondUnauthorizedProblemWhenAuthenticationIsRequired() throws Exception {
		// given
		var request = new MockHttpServletRequest("GET", "/api/v1/users");
		var response = new MockHttpServletResponse();

		// when
		handlers.commence(request, response, new InvalidBearerTokenException("Jwt expired at 2026-09-26T10:00:00Z"));

		// then
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
		assertThat(body(response)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"title", "Unauthorized",
				"status", 401,
				"detail", "Autenticação necessária",
				"instance", "/api/v1/users"));
	}

	@Test
	void shouldNotRevealRefusalReasonWhenAuthenticationFails() throws Exception {
		// given
		var request = new MockHttpServletRequest("GET", "/api/v1/users");
		var response = new MockHttpServletResponse();

		// when
		handlers.commence(request, response, new InvalidBearerTokenException("Jwt expired at 2026-09-26T10:00:00Z"));

		// then
		assertThat(response.getHeaders(HttpHeaders.WWW_AUTHENTICATE)).containsExactly("Bearer");
		assertThat(response.getContentAsString()).doesNotContain("expired", "2026-09-26", "invalid_token",
				"error_description");
	}

	@Test
	void shouldRespondForbiddenProblemWhenAccessIsDenied() throws Exception {
		// given
		var request = new MockHttpServletRequest("DELETE", "/api/v1/users/42");
		var response = new MockHttpServletResponse();

		// when
		handlers.handle(request, response, new AccessDeniedException("Access Denied"));

		// then
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
		assertThat(body(response)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"title", "Forbidden",
				"status", 403,
				"detail", "Acesso negado",
				"instance", "/api/v1/users/42"));
	}

	@Test
	void shouldOmitInstanceWhenRequestPathIsNotValidUri() throws Exception {
		// given
		var request = new MockHttpServletRequest("GET", "/api/v1/users/a b");
		var response = new MockHttpServletResponse();

		// when
		handlers.commence(request, response, new InvalidBearerTokenException("Invalid token"));

		// then
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(body(response)).containsOnlyKeys("title", "status", "detail");
	}

	private Map<String, Object> body(MockHttpServletResponse response) {
		return jsonMapper.readValue(response.getContentAsByteArray(), new TypeReference<>() {
		});
	}

}
