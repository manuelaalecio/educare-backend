package com.manuelaalecio.educare_backend.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.error.GlobalExceptionHandler;
import com.manuelaalecio.educare_backend.user.application.UserService;
import com.manuelaalecio.educare_backend.user.domain.LoginAlreadyInUseException;
import com.manuelaalecio.educare_backend.user.domain.Role;
import com.manuelaalecio.educare_backend.user.domain.User;
import com.manuelaalecio.educare_backend.user.domain.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, UserMapper.class})
class UserControllerTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";
	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String VALID_CREATE_BODY = """
			{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123"}""";
	private static final String VALID_UPDATE_BODY = """
			{"name": "Ana Souza", "login": "ana.souza@educare.org", "role": "ADMIN"}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	// ---------- POST /api/v1/users ----------

	@Test
	void shouldCreateUserAndReturnLocationWhenBodyIsValid() throws Exception {
		// given
		given(userService.create("Ana Souza", "ana.souza@educare.org", "segredo123", null))
				.willReturn(user(Role.USER));

		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", VALID_CREATE_BODY));

		// then
		result.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/users/" + ID))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.login").value("ana.souza@educare.org"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString("segredo123"))))
				.andExpect(content().string(not(containsString(PASSWORD_HASH))));
	}

	@Test
	void shouldPassNullRoleToServiceWhenRoleIsAbsentOnCreate() throws Exception {
		// given
		given(userService.create(anyString(), anyString(), anyString(), isNull())).willReturn(user(Role.USER));

		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", VALID_CREATE_BODY));

		// then
		result.andExpect(status().isCreated());
		then(userService).should().create("Ana Souza", "ana.souza@educare.org", "segredo123", null);
	}

	@Test
	void shouldPassAdminRoleToServiceWhenRoleIsAdminOnCreate() throws Exception {
		// given
		given(userService.create(anyString(), anyString(), anyString(), eq(Role.ADMIN))).willReturn(user(Role.ADMIN));

		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123", "role": "ADMIN"}"""));

		// then
		result.andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"));
		then(userService).should().create("Ana Souza", "ana.souza@educare.org", "segredo123", Role.ADMIN);
	}

	@Test
	void shouldReturnErrorsForNameLoginAndPasswordWhenCreateBodyIsEmpty() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", "{}"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "login", "password")))
				.andExpect(jsonPath("$.errors[*].message").value(hasItem("é obrigatório")));
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenLoginIsNotAnEmail() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana.souza", "password": "segredo123"}"""));

		// then
		expectSingleFieldError(result, "login");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenLoginDomainHasNoDot() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana@educare", "password": "segredo123"}"""));

		// then
		expectSingleFieldError(result, "login");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenLoginIsLongerThan254Characters() throws Exception {
		// given
		String login = "a".repeat(243) + "@educare.org";

		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "%s", "password": "segredo123"}""".formatted(login)));

		// then
		assertThat(login).hasSize(255);
		expectSingleFieldError(result, "login");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenNameIsBlank() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "   ", "login": "ana.souza@educare.org", "password": "segredo123"}"""));

		// then
		expectSingleFieldError(result, "name");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenNameIsLongerThan150Characters() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "%s", "login": "ana.souza@educare.org", "password": "segredo123"}""".formatted("a".repeat(151))));

		// then
		expectSingleFieldError(result, "name");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenPasswordIsShorterThan8Characters() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "1234567"}"""));

		// then
		expectSingleFieldError(result, "password");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenPasswordIsLongerThan72Characters() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "%s"}""".formatted("a".repeat(73))));

		// then
		expectSingleFieldError(result, "password");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenPasswordExceeds72BytesInUtf8() throws Exception {
		// given 40 characters, 80 bytes in UTF-8
		String password = "ç".repeat(40);

		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "%s"}""".formatted(password)));

		// then
		expectSingleFieldError(result, "password");
		result.andExpect(jsonPath("$.errors[0].message").value("deve ocupar no máximo 72 bytes em UTF-8"));
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenRoleIsNotAdminNorUser() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123", "role": "SUPER"}"""));

		// then
		expectSingleFieldError(result, "role");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectCreateWhenRoleIsNotUpperCase() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123", "role": "admin"}"""));

		// then
		expectSingleFieldError(result, "role");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturnConflictWithoutEmailWhenLoginIsAlreadyInUse() throws Exception {
		// given
		given(userService.create(any(), any(), any(), any())).willThrow(new LoginAlreadyInUseException());

		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", """
				{"name": "Ana Souza", "login": "ANA.SOUZA@EDUCARE.ORG", "password": "segredo123"}"""));

		// then
		result.andExpect(status().isConflict())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Login já está em uso"))
				.andExpect(content().string(not(containsString("ana.souza@educare.org"))))
				.andExpect(content().string(not(containsString("ANA.SOUZA@EDUCARE.ORG"))));
	}

	@Test
	void shouldReturnBadRequestWhenCreateBodyIsNotValidJson() throws Exception {
		// when
		ResultActions result = mockMvc.perform(postJson("/api/v1/users", "{\"name\": \"Ana\""));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(userService).shouldHaveNoInteractions();
	}

	// ---------- GET /api/v1/users ----------

	@Test
	void shouldReturnPagedModelWithoutPasswordWhenListingUsers() throws Exception {
		// given
		given(userService.list(any())).willAnswer(invocation -> page(invocation.getArgument(0), 3, user(Role.USER)));

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users").param("size", "2"));

		// then
		result.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.content[0].id").value(ID.toString()))
				.andExpect(jsonPath("$.content[0].name").value("Ana Souza"))
				.andExpect(jsonPath("$.content[0].login").value("ana.souza@educare.org"))
				.andExpect(jsonPath("$.content[0].role").value("USER"))
				.andExpect(jsonPath("$.content[0].createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.content[0].updatedAt").value("2026-03-02T09:00:00Z"))
				.andExpect(jsonPath("$.content[0].password").doesNotExist())
				.andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString(PASSWORD_HASH))))
				.andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(3))
				.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void shouldUseFirstPageOfTwentySortedByNameWhenNoParametersAreGiven() throws Exception {
		// given
		given(userService.list(any())).willAnswer(invocation -> page(invocation.getArgument(0), 0));

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.page.totalElements").value(0));
		Pageable pageable = capturedPageable();
		assertThat(pageable.getPageNumber()).isZero();
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("name")));
	}

	@Test
	void shouldLimitPageSizeTo100WhenRequestedSizeIsLarger() throws Exception {
		// given
		given(userService.list(any())).willAnswer(invocation -> page(invocation.getArgument(0), 1, user(Role.USER)));

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users").param("size", "500"));

		// then
		result.andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(100));
		assertThat(capturedPageable().getPageSize()).isEqualTo(100);
	}

	@Test
	void shouldPassRequestedSortToServiceWhenSortingByLoginDescending() throws Exception {
		// given
		given(userService.list(any())).willAnswer(invocation -> page(invocation.getArgument(0), 0));

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users").param("sort", "login,desc").param("page", "1"));

		// then
		result.andExpect(status().isOk());
		Pageable pageable = capturedPageable();
		assertThat(pageable.getPageNumber()).isEqualTo(1);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("login")));
	}

	@Test
	void shouldAcceptSortWhenSortingByCreatedAt() throws Exception {
		// given
		given(userService.list(any())).willAnswer(invocation -> page(invocation.getArgument(0), 0));

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users").param("sort", "createdAt,asc"));

		// then
		result.andExpect(status().isOk());
		assertThat(capturedPageable().getSort()).isEqualTo(Sort.by(Sort.Order.asc("createdAt")));
	}

	@Test
	void shouldReturnBadRequestWhenSortingByPassword() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users").param("sort", "password"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturnBadRequestWhenSortingByPasswordHashAfterAnAllowedProperty() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users")
				.param("sort", "name,asc")
				.param("sort", "passwordHash,desc"));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(userService).shouldHaveNoInteractions();
	}

	// ---------- GET /api/v1/users/{id} ----------

	@Test
	void shouldReturnUserWithoutPasswordWhenUserExists() throws Exception {
		// given
		given(userService.findById(ID)).willReturn(user(Role.USER));

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users/{id}", ID));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.login").value("ana.souza@educare.org"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString(PASSWORD_HASH))));
	}

	@Test
	void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
		// given
		given(userService.findById(ID)).willThrow(new UserNotFoundException());

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users/{id}", ID));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Usuário não encontrado"));
	}

	@Test
	void shouldReturnBadRequestWhenIdIsNotAUuid() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/api/v1/users/abc"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
		then(userService).shouldHaveNoInteractions();
	}

	// ---------- PUT /api/v1/users/{id} ----------

	@Test
	void shouldUpdateUserWhenBodyIsValid() throws Exception {
		// given
		given(userService.update(ID, "Ana Souza", "ana.souza@educare.org", Role.ADMIN)).willReturn(user(Role.ADMIN));

		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, VALID_UPDATE_BODY));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.login").value("ana.souza@educare.org"))
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString(PASSWORD_HASH))));
	}

	@Test
	void shouldRejectUpdateWhenRoleIsAbsent() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, """
				{"name": "Ana Souza", "login": "ana.souza@educare.org"}"""));

		// then
		expectSingleFieldError(result, "role");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectUpdateWhenRoleIsNotAdminNorUser() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "role": "SUPER"}"""));

		// then
		expectSingleFieldError(result, "role");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturnErrorsForNameAndLoginWhenUpdateDataIsInvalid() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, """
				{"name": "", "login": "ana", "role": "USER"}"""));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "login")));
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectUpdateWhenLoginDomainHasNoDot() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, """
				{"name": "Ana Souza", "login": "ana@educare", "role": "USER"}"""));

		// then
		expectSingleFieldError(result, "login");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturnNotFoundWhenUpdatingUserThatDoesNotExist() throws Exception {
		// given
		given(userService.update(any(), any(), any(), any())).willThrow(new UserNotFoundException());

		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, VALID_UPDATE_BODY));

		// then
		result.andExpect(status().isNotFound()).andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	void shouldReturnConflictWithoutEmailWhenUpdatingToLoginOfAnotherUser() throws Exception {
		// given
		given(userService.update(any(), any(), any(), any())).willThrow(new LoginAlreadyInUseException());

		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, VALID_UPDATE_BODY));

		// then
		result.andExpect(status().isConflict())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(content().string(not(containsString("ana.souza@educare.org"))));
	}

	@Test
	void shouldReturnBadRequestWhenUpdateBodyIsNotValidJson() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID, "{\"name\": \"Ana\""));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturnBadRequestWhenUpdatingWithMalformedId() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/abc", VALID_UPDATE_BODY));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(userService).shouldHaveNoInteractions();
	}

	// ---------- PUT /api/v1/users/{id}/password ----------

	@Test
	void shouldChangePasswordAndReturnNoContentWhenPasswordIsValid() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID + "/password", """
				{"password": "novaSenha456"}"""));

		// then
		result.andExpect(status().isNoContent()).andExpect(content().string(""));
		then(userService).should().changePassword(ID, "novaSenha456");
	}

	@Test
	void shouldRejectPasswordChangeWhenPasswordIsTooShort() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID + "/password", """
				{"password": "curta"}"""));

		// then
		expectSingleFieldError(result, "password");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectPasswordChangeWhenPasswordIsAbsent() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID + "/password", "{}"));

		// then
		expectSingleFieldError(result, "password");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectPasswordChangeWhenPasswordExceeds72BytesInUtf8() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID + "/password", """
				{"password": "%s"}""".formatted("ç".repeat(40))));

		// then
		expectSingleFieldError(result, "password");
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturnNotFoundWhenChangingPasswordOfUserThatDoesNotExist() throws Exception {
		// given
		willThrow(new UserNotFoundException()).given(userService).changePassword(ID, "novaSenha456");

		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/" + ID + "/password", """
				{"password": "novaSenha456"}"""));

		// then
		result.andExpect(status().isNotFound()).andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	void shouldReturnBadRequestWhenChangingPasswordWithMalformedId() throws Exception {
		// when
		ResultActions result = mockMvc.perform(putJson("/api/v1/users/abc/password", """
				{"password": "novaSenha456"}"""));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(userService).shouldHaveNoInteractions();
	}

	// ---------- DELETE /api/v1/users/{id} ----------

	@Test
	void shouldDeleteUserAndReturnNoContentWhenUserExists() throws Exception {
		// when
		ResultActions result = mockMvc.perform(delete("/api/v1/users/{id}", ID));

		// then
		result.andExpect(status().isNoContent()).andExpect(content().string(""));
		then(userService).should().delete(ID);
	}

	@Test
	void shouldReturnNotFoundWhenDeletingUserThatDoesNotExist() throws Exception {
		// given
		willThrow(new UserNotFoundException()).given(userService).delete(ID);

		// when
		ResultActions result = mockMvc.perform(delete("/api/v1/users/{id}", ID));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void shouldReturnBadRequestWhenDeletingWithMalformedId() throws Exception {
		// when
		ResultActions result = mockMvc.perform(delete("/api/v1/users/abc"));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(userService).should(never()).delete(any());
	}

	// ---------- helpers ----------

	private static User user(Role role) {
		User user = new User("Ana Souza", "ana.souza@educare.org", PASSWORD_HASH, role);
		ReflectionTestUtils.setField(user, "id", ID);
		ReflectionTestUtils.setField(user, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(user, "updatedAt", UPDATED_AT);
		return user;
	}

	private static Page<User> page(Pageable pageable, long total, User... users) {
		return new PageImpl<>(List.of(users), pageable, total);
	}

	private Pageable capturedPageable() {
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		then(userService).should().list(captor.capture());
		return captor.getValue();
	}

	private static MockHttpServletRequestBuilder postJson(String uri,
			String body) {
		return post(uri).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockHttpServletRequestBuilder putJson(String uri,
			String body) {
		return put(uri).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static void expectSingleFieldError(ResultActions result, String field) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem(field)))
				.andExpect(jsonPath("$.errors[*].field").value(everyItem(equalTo(field))));
	}

}
