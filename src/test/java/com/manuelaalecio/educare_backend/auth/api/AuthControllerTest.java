package com.manuelaalecio.educare_backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.manuelaalecio.educare_backend.auth.application.AuthService;
import com.manuelaalecio.educare_backend.auth.domain.InvalidCredentialsException;
import com.manuelaalecio.educare_backend.shared.error.GlobalExceptionHandler;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUserLookup;
import com.manuelaalecio.educare_backend.shared.security.IssuedAccessToken;
import com.manuelaalecio.educare_backend.shared.security.JwtConfiguration;
import com.manuelaalecio.educare_backend.shared.security.ProblemDetailSecurityHandlers;
import com.manuelaalecio.educare_backend.shared.security.SecurityConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.AccessTokens;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import com.manuelaalecio.educare_backend.user.application.UserAccount;
import com.manuelaalecio.educare_backend.user.domain.InvalidCurrentPasswordException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AuthController.class)
@Import({AuthMapper.class, SecurityConfiguration.class, JwtConfiguration.class, MutableClockConfiguration.class,
		ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class, AccessTokens.class})
class AuthControllerTest {

	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final String LOGIN_PATH = "/api/v1/auth/login";
	private static final String ME_PATH = "/api/v1/auth/me";
	private static final String PASSWORD_PATH = "/api/v1/auth/me/password";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String TOKEN = "header.payload.signature";
	private static final String VALID_PASSWORD_CHANGE_BODY = """
			{"currentPassword": "segredo123", "newPassword": "novaSenha456"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccessTokens accessTokens;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private AuthenticatedUserLookup lookup;

	// ---------- POST /api/v1/auth/login ----------

	@Test
	void shouldReturnBearerTokenWhenCredentialsAreValid() throws Exception {
		// given
		given(authService.login("ana.souza@educare.org", "segredo123"))
				.willReturn(new IssuedAccessToken(TOKEN, 28800));

		// when
		ResultActions result = login("""
				{"login": "ana.souza@educare.org", "password": "segredo123"}""");

		// then
		result.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.accessToken").value(TOKEN))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(28800));
	}

	@Test
	void shouldRejectLoginWithFieldErrorsWhenFieldsAreMissing() throws Exception {
		// when
		ResultActions result = login("{}");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("login", "password")));
		then(authService).should(never()).login(anyString(), anyString());
	}

	@Test
	void shouldRejectLoginWithFieldErrorsWhenFieldsAreBlank() throws Exception {
		// when
		ResultActions result = login("""
				{"login": "  ", "password": " "}""");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("login", "password")));
		then(authService).should(never()).login(anyString(), anyString());
	}

	@Test
	void shouldRejectLoginWhenPasswordHasMoreThan72Bytes() throws Exception {
		// given
		String password = "ç".repeat(40);

		// when
		ResultActions result = login("""
				{"login": "ana.souza@educare.org", "password": "%s"}""".formatted(password));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
				.andExpect(jsonPath("$.errors[*].field").value(not(hasItem("login"))));
		then(authService).should(never()).login(anyString(), anyString());
	}

	@Test
	void shouldRejectLoginWhenPasswordHasMoreThan72Characters() throws Exception {
		// when
		ResultActions result = login("""
				{"login": "ana.souza@educare.org", "password": "%s"}""".formatted("a".repeat(73)));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
		then(authService).should(never()).login(anyString(), anyString());
	}

	@Test
	void shouldRejectLoginWhenLoginHasMoreThan254Characters() throws Exception {
		// given
		String login = "a".repeat(243) + "@educare.org";

		// when
		ResultActions result = login("""
				{"login": "%s", "password": "segredo123"}""".formatted(login));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("login"));
		then(authService).should(never()).login(anyString(), anyString());
	}

	@Test
	void shouldRejectLoginWhenBodyIsNotValidJson() throws Exception {
		// when
		ResultActions result = login("{\"login\": \"ana");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
		then(authService).should(never()).login(anyString(), anyString());
	}

	@Test
	void shouldRespondSameUnauthorizedProblemWhenLoginDoesNotExistOrPasswordIsWrong() throws Exception {
		// given
		given(authService.login("ninguem@educare.org", "segredo123")).willThrow(new InvalidCredentialsException());
		given(authService.login("ana.souza@educare.org", "errada123")).willThrow(new InvalidCredentialsException());

		// when
		ResultActions unknownLogin = login("""
				{"login": "ninguem@educare.org", "password": "segredo123"}""");
		ResultActions wrongPassword = login("""
				{"login": "ana.souza@educare.org", "password": "errada123"}""");

		// then
		assertInvalidCredentials(unknownLogin, "ninguem@educare.org");
		assertInvalidCredentials(wrongPassword, "ana.souza@educare.org");
		String unknownBody = unknownLogin.andReturn().getResponse().getContentAsString();
		String wrongBody = wrongPassword.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(unknownBody, "$.title")).isEqualTo(JsonPath.read(wrongBody, "$.title"));
		assertThat(JsonPath.<String>read(unknownBody, "$.detail")).isEqualTo(JsonPath.read(wrongBody, "$.detail"));
	}

	// ---------- GET /api/v1/auth/me ----------

	@Test
	void shouldReturnOwnDataWithoutPasswordWhenTokenIsValid() throws Exception {
		// given
		givenUser("USER");
		given(authService.me(USER_ID)).willReturn(new UserAccount(USER_ID, "Ana Souza", "ana.souza@educare.org",
				"USER", CREATED_AT, UPDATED_AT));

		// when
		ResultActions result = mockMvc.perform(get(ME_PATH)
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(USER_ID)));

		// then
		result.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(USER_ID.toString()))
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.login").value("ana.souza@educare.org"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void shouldRespondUnauthorizedWhenMeIsCalledWithoutToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(ME_PATH));

		// then
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(PROBLEM_JSON));
		then(authService).should(never()).me(any());
	}

	// ---------- PUT /api/v1/auth/me/password ----------

	@Test
	void shouldChangeOwnPasswordWhenBodyIsValid() throws Exception {
		// given
		givenUser("USER");

		// when
		ResultActions result = changePassword(VALID_PASSWORD_CHANGE_BODY);

		// then
		result.andExpect(status().isNoContent())
				.andExpect(content().string(""));
		then(authService).should().changeOwnPassword(USER_ID, "segredo123", "novaSenha456");
	}

	@Test
	void shouldRejectPasswordChangeWhenNewPasswordIsInvalid() throws Exception {
		// given
		givenUser("USER");

		// when
		ResultActions result = changePassword("""
				{"currentPassword": "segredo123", "newPassword": "curta"}""");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
		then(authService).should(never()).changeOwnPassword(any(), anyString(), anyString());
	}

	@Test
	void shouldRejectPasswordChangeWhenNewPasswordHasMoreThan72Bytes() throws Exception {
		// given
		givenUser("USER");

		// when
		ResultActions result = changePassword("""
				{"currentPassword": "segredo123", "newPassword": "%s"}""".formatted("ç".repeat(40)));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("newPassword")))
				.andExpect(jsonPath("$.errors[*].field").value(not(hasItem("currentPassword"))));
		then(authService).should(never()).changeOwnPassword(any(), anyString(), anyString());
	}

	@Test
	void shouldRejectPasswordChangeWithFieldErrorsWhenFieldsAreMissing() throws Exception {
		// given
		givenUser("USER");

		// when
		ResultActions result = changePassword("{}");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field")
						.value(containsInAnyOrder("currentPassword", "newPassword")));
		then(authService).should(never()).changeOwnPassword(any(), any(), any());
	}

	@Test
	void shouldRejectPasswordChangeWhenCurrentPasswordIsIncorrect() throws Exception {
		// given
		givenUser("USER");
		willThrow(new InvalidCurrentPasswordException()).given(authService)
				.changeOwnPassword(USER_ID, "errada123", "novaSenha456");

		// when
		ResultActions result = changePassword("""
				{"currentPassword": "errada123", "newPassword": "novaSenha456"}""");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
				.andExpect(jsonPath("$.errors[0].message").value("Senha atual incorreta"));
	}

	@Test
	void shouldRejectPasswordChangeWhenBodyIsNotValidJson() throws Exception {
		// given
		givenUser("USER");

		// when
		ResultActions result = changePassword("{\"currentPassword\": ");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON));
		then(authService).should(never()).changeOwnPassword(any(), any(), any());
	}

	@Test
	void shouldRespondUnauthorizedWhenPasswordChangeIsCalledWithoutToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(put(PASSWORD_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_PASSWORD_CHANGE_BODY));

		// then
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(content().string(not(containsString("segredo123"))));
		then(authService).should(never()).changeOwnPassword(any(), any(), any());
	}

	private ResultActions login(String body) throws Exception {
		return mockMvc.perform(post(LOGIN_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions changePassword(String body) throws Exception {
		return mockMvc.perform(put(PASSWORD_PATH)
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(USER_ID))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static void assertInvalidCredentials(ResultActions result, String login) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.detail").value("Login ou senha inválidos"))
				.andExpect(jsonPath("$.accessToken").doesNotExist())
				.andExpect(content().string(not(containsString(login))));
	}

	private void givenUser(String role) {
		given(lookup.findById(USER_ID)).willReturn(Optional.of(new AuthenticatedUser(USER_ID, role)));
	}

}
