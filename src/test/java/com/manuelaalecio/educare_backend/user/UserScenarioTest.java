package com.manuelaalecio.educare_backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class})
class UserScenarioTest {

	private static final String USERS = "/api/v1/users";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String MISSING_ID = "0190f4a2-0000-7000-8000-000000000000";
	private static final String ZELIA_LOGIN = "zelia@educare.org";
	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String BRUNO_LOGIN = "bruno.lima@educare.org";
	private static final String PASSWORD = "segredo123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MutableClock clock;

	@Autowired
	private TestUsers testUsers;

	private UUID zeliaId;
	private String zeliaBearer;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("DELETE FROM users");
		clock.setInstant(CREATED_AT);
		zeliaId = testUsers.create("Zélia", ZELIA_LOGIN, PASSWORD, "ADMIN");
		zeliaBearer = testUsers.bearer(ZELIA_LOGIN, PASSWORD);
	}

	// ---------- Criar usuário ----------

	@Test
	@DisplayName("Scenario: Usuário criado com dados válidos")
	void shouldCreateUserWhenDataIsValid() throws Exception {
		// given
		clock.setInstant(CREATED_AT);

		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.login").value(ANA_LOGIN))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:00:00Z"))
				.andReturn();
		UUID id = idOf(mvcResult);
		assertThat(mvcResult.getResponse().getHeader("Location")).isEqualTo(USERS + "/" + id);
		assertThat(countUsersBesidesZelia()).isEqualTo(1);
	}

	@Test
	@DisplayName("Scenario: Nome com espaços nas pontas é gravado sem eles")
	void shouldTrimNameWhenNameHasSurroundingSpaces() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "  Ana Souza  ", "login": "ana.souza@educare.org", "password": "segredo123"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andReturn();
		assertThat(nameInDatabase(idOf(mvcResult))).isEqualTo("Ana Souza");
	}

	@Test
	@DisplayName("Scenario: Campos obrigatórios ausentes")
	void shouldRejectCreationWhenRequiredFieldsAreMissing() throws Exception {
		// when
		ResultActions result = postUser("{}");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "login", "password")))
				.andExpect(jsonPath("$.errors[*].field").value(not(hasItem("role"))));
		assertThat(countUsersBesidesZelia()).isZero();
	}

	@Test
	@DisplayName("Scenario: Login que não é um email")
	void shouldRejectCreationWhenLoginIsNotAnEmail() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza", "password": "segredo123"}""");

		// then
		assertValidationError(result, "login");
		assertThat(countUsersBesidesZelia()).isZero();
	}

	@Test
	@DisplayName("Scenario: Email sem domínio completo")
	void shouldRejectCreationWhenEmailDomainHasNoDot() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana@educare", "password": "segredo123"}""");

		// then
		assertValidationError(result, "login");
		assertThat(countUsersBesidesZelia()).isZero();
	}

	@Test
	@DisplayName("Scenario: Senha curta demais")
	void shouldRejectCreationWhenPasswordIsTooShort() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "1234567"}""");

		// then
		assertValidationError(result, "password");
		assertThat(countUsersBesidesZelia()).isZero();
	}

	@Test
	@DisplayName("Scenario: Senha acima de 72 bytes")
	void shouldRejectCreationWhenPasswordExceeds72Bytes() throws Exception {
		// given
		String password = "ç".repeat(40);

		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "%s"}""".formatted(password));

		// then
		assertValidationError(result, "password");
		assertThat(countUsersBesidesZelia()).isZero();
	}

	@Test
	@DisplayName("Scenario: Nome longo demais")
	void shouldRejectCreationWhenNameIsTooLong() throws Exception {
		// given
		String name = "a".repeat(151);

		// when
		ResultActions result = postUser("""
				{"name": "%s", "login": "ana.souza@educare.org", "password": "segredo123"}""".formatted(name));

		// then
		assertValidationError(result, "name");
		assertThat(countUsersBesidesZelia()).isZero();
	}

	// ---------- Login por email único ----------

	@Test
	@DisplayName("Scenario: Login com maiúsculas é gravado em minúsculas")
	void shouldStoreLoginInLowerCaseWhenLoginHasUpperCase() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "Ana.Souza@Educare.org", "password": "segredo123"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.login").value(ANA_LOGIN))
				.andReturn();
		assertThat(loginInDatabase(idOf(mvcResult))).isEqualTo(ANA_LOGIN);
	}

	@Test
	@DisplayName("Scenario: Criação com login já usado")
	void shouldRejectCreationWhenLoginIsAlreadyUsed() throws Exception {
		// given
		createAna();

		// when
		ResultActions result = postUser("""
				{"name": "Outra Ana", "login": "ANA.SOUZA@EDUCARE.ORG", "password": "segredo123"}""");

		// then
		result.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(content().string(not(containsString(ANA_LOGIN))))
				.andExpect(content().string(not(containsString("ANA.SOUZA@EDUCARE.ORG"))));
		assertThat(countUsersWithLogin(ANA_LOGIN)).isEqualTo(1);
		assertThat(countUsersBesidesZelia()).isEqualTo(1);
	}

	@Test
	@DisplayName("Scenario: Alteração para login de outro usuário")
	void shouldRejectUpdateWhenLoginBelongsToAnotherUser() throws Exception {
		// given
		createAna();
		UUID brunoId = createUser("Bruno Lima", "bruno.lima@educare.org", "segredo123", null);

		// when
		ResultActions result = putUser(brunoId, """
				{"name": "Bruno Lima", "login": "ana.souza@educare.org", "role": "USER"}""");

		// then
		result.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
		assertThat(loginInDatabase(brunoId)).isEqualTo("bruno.lima@educare.org");
		assertThat(countUsersWithLogin(ANA_LOGIN)).isEqualTo(1);
	}

	// ---------- Papel do usuário ----------

	@Test
	@DisplayName("Scenario: Criação sem role vira USER")
	void shouldCreateUserWithUserRoleWhenRoleIsAbsent() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"))
				.andReturn();
		assertThat(roleInDatabase(idOf(mvcResult))).isEqualTo("USER");
	}

	@Test
	@DisplayName("Scenario: Criação como ADMIN")
	void shouldCreateAdminWhenRoleIsAdmin() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123", "role": "ADMIN"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andReturn();
		performAsZelia(get(USERS + "/{id}", idOf(mvcResult)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	@DisplayName("Scenario: Role inválida")
	void shouldRejectCreationWhenRoleIsInvalid() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123", "role": "SUPER"}""");

		// then
		assertValidationError(result, "role");
		assertThat(countUsersBesidesZelia()).isZero();
	}

	@Test
	@DisplayName("Scenario: Promoção de USER para ADMIN")
	void shouldPromoteUserToAdminWhenUpdatedWithAdminRole() throws Exception {
		// given
		UUID id = createAna();

		// when
		ResultActions result = putUser(id, """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "role": "ADMIN"}""");

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
		assertThat(roleInDatabase(id)).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("Scenario: Alteração sem role")
	void shouldRejectUpdateWhenRoleIsAbsent() throws Exception {
		// given
		UUID id = createAna();

		// when
		ResultActions result = putUser(id, """
				{"name": "Ana Souza", "login": "ana.souza@educare.org"}""");

		// then
		assertValidationError(result, "role");
		assertThat(roleInDatabase(id)).isEqualTo("USER");
	}

	// ---------- Senha protegida ----------

	@Test
	@DisplayName("Scenario: Senha não é guardada em texto")
	void shouldStoreOnlyPasswordHashWhenUserIsCreated() throws Exception {
		// when
		UUID id = createAna();

		// then
		String stored = passwordHashInDatabase(id);
		assertThat(stored).isNotEqualTo("segredo123");
		assertThat(passwordEncoder.matches("segredo123", stored)).isTrue();
	}

	@Test
	@DisplayName("Scenario: Respostas não expõem a senha")
	void shouldNotExposePasswordWhenUserIsCreatedFetchedAndListed() throws Exception {
		// given
		MvcResult created = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123"}""")
				.andExpect(status().isCreated())
				.andReturn();
		UUID id = idOf(created);
		String hash = passwordHashInDatabase(id);

		// when
		MvcResult fetched = performAsZelia(get(USERS + "/{id}", id)).andExpect(status().isOk()).andReturn();
		MvcResult listed = performAsZelia(get(USERS)).andExpect(status().isOk()).andReturn();

		// then
		for (MvcResult response : new MvcResult[] {created, fetched, listed}) {
			String body = response.getResponse().getContentAsString();
			assertThat(body)
					.doesNotContainIgnoringCase("\"password")
					.doesNotContain("segredo123")
					.doesNotContain(hash);
		}
		assertThat(listed.getResponse().getContentAsString()).contains(id.toString());
	}

	// ---------- Consultar usuário por id ----------

	@Test
	@DisplayName("Scenario: Usuário existente")
	void shouldReturnUserWhenIdExists() throws Exception {
		// given
		clock.setInstant(CREATED_AT);
		UUID id = createAna();

		// when
		ResultActions result = performAsZelia(get(USERS + "/{id}", id));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.login").value(ANA_LOGIN))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:00:00Z"));
	}

	@Test
	@DisplayName("Scenario: Usuário inexistente")
	void shouldReturnNotFoundWhenIdDoesNotExist() throws Exception {
		// when
		ResultActions result = performAsZelia(get(USERS + "/" + MISSING_ID));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Scenario: Id malformado")
	void shouldReturnBadRequestWhenIdIsMalformed() throws Exception {
		// when
		ResultActions result = performAsZelia(get(USERS + "/abc"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	// ---------- Listar usuários ----------

	@Test
	@DisplayName("Scenario: Primeira página ordenada por nome")
	void shouldReturnFirstPageSortedByNameWhenSizeIsGiven() throws Exception {
		// given
		createUser("Carla", "carla@educare.org", "segredo123", null);
		createUser("Ana", "ana@educare.org", "segredo123", null);
		createUser("Bruno", "bruno@educare.org", "segredo123", null);

		// when
		ResultActions result = performAsZelia(get(USERS).param("size", "2"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name").value(contains("Ana", "Bruno")))
				.andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(4))
				.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	@DisplayName("Scenario: Ordenação decrescente por login")
	void shouldSortByLoginDescendingWhenRequested() throws Exception {
		// given
		createUser("Ana", "ana@educare.org", "segredo123", null);
		createUser("Bruno", "bruno@educare.org", "segredo123", null);
		createUser("Carla", "carla@educare.org", "segredo123", null);

		// when
		ResultActions result = performAsZelia(get(USERS).param("sort", "login,desc"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].login")
						.value(contains(ZELIA_LOGIN, "carla@educare.org", "bruno@educare.org", "ana@educare.org")));
	}

	@Test
	@DisplayName("Scenario: Nenhum usuário cadastrado")
	void shouldReturnOnlyTheRequestingAdminWhenThereAreNoOtherUsers() throws Exception {
		// when
		ResultActions result = performAsZelia(get(USERS));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].login").value(contains(ZELIA_LOGIN)))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	@DisplayName("Scenario: Tamanho de página acima do máximo")
	void shouldCapPageSizeAt100WhenSizeIsAboveMaximum() throws Exception {
		// given
		UUID ana = createUser("Ana", "ana@educare.org", "segredo123", null);
		UUID bruno = createUser("Bruno", "bruno@educare.org", "segredo123", null);
		UUID carla = createUser("Carla", "carla@educare.org", "segredo123", null);

		// when
		ResultActions result = performAsZelia(get(USERS).param("size", "500"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.size").value(100))
				.andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
						zeliaId.toString(), ana.toString(), bruno.toString(), carla.toString())));
	}

	@Test
	@DisplayName("Scenario: Ordenação por propriedade não permitida")
	void shouldRejectListingWhenSortPropertyIsNotAllowed() throws Exception {
		// when
		ResultActions result = performAsZelia(get(USERS).param("sort", "password"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	// ---------- Alterar nome, login e role ----------

	@Test
	@DisplayName("Scenario: Alteração com dados válidos")
	void shouldUpdateUserWhenDataIsValid() throws Exception {
		// given
		clock.setInstant(CREATED_AT);
		UUID id = createAna();
		String hashBefore = passwordHashInDatabase(id);
		clock.setInstant(UPDATED_AT);
		zeliaBearer = testUsers.bearer(ZELIA_LOGIN, PASSWORD);

		// when
		ResultActions result = putUser(id, """
				{"name": "Ana Souza Lima", "login": "ana.lima@educare.org", "role": "USER"}""");

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana Souza Lima"))
				.andExpect(jsonPath("$.login").value("ana.lima@educare.org"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"));
		String hashAfter = passwordHashInDatabase(id);
		assertThat(hashAfter).isEqualTo(hashBefore);
		assertThat(passwordEncoder.matches("segredo123", hashAfter)).isTrue();
	}

	@Test
	@DisplayName("Scenario: Manter o próprio login")
	void shouldKeepOwnLoginWhenUpdatedWithSameLoginInDifferentCase() throws Exception {
		// given
		UUID id = createAna();

		// when
		ResultActions result = putUser(id, """
				{"name": "Ana S.", "login": "ANA.SOUZA@EDUCARE.ORG", "role": "USER"}""");

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana S."))
				.andExpect(jsonPath("$.login").value(ANA_LOGIN));
		assertThat(loginInDatabase(id)).isEqualTo(ANA_LOGIN);
	}

	@Test
	@DisplayName("Scenario: Alteração com dados inválidos")
	void shouldRejectUpdateWhenDataIsInvalid() throws Exception {
		// given
		UUID id = createAna();

		// when
		ResultActions result = putUser(id, """
				{"name": "", "login": "ana", "role": "USER"}""");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("login")));
		assertThat(nameInDatabase(id)).isEqualTo("Ana Souza");
		assertThat(loginInDatabase(id)).isEqualTo(ANA_LOGIN);
	}

	@Test
	@DisplayName("Scenario: Alteração de usuário inexistente")
	void shouldReturnNotFoundWhenUpdatingMissingUser() throws Exception {
		// when
		ResultActions result = performAsZelia(put(USERS + "/" + MISSING_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ana Souza", "login": "ana.souza@educare.org", "role": "USER"}"""));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
		assertThat(countUsersBesidesZelia()).isZero();
	}

	// ---------- Alterar senha ----------

	@Test
	@DisplayName("Scenario: Senha alterada")
	void shouldChangePasswordWhenNewPasswordIsValid() throws Exception {
		// given
		UUID id = createAna();

		// when
		ResultActions result = putPassword(id, """
				{"password": "novaSenha456"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isNoContent()).andReturn();
		assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
		String stored = passwordHashInDatabase(id);
		assertThat(passwordEncoder.matches("novaSenha456", stored)).isTrue();
		assertThat(passwordEncoder.matches("segredo123", stored)).isFalse();
	}

	@Test
	@DisplayName("Scenario: Nova senha inválida")
	void shouldRejectPasswordChangeWhenNewPasswordIsInvalid() throws Exception {
		// given
		UUID id = createAna();

		// when
		ResultActions result = putPassword(id, """
				{"password": "curta"}""");

		// then
		assertValidationError(result, "password");
		assertThat(passwordEncoder.matches("segredo123", passwordHashInDatabase(id))).isTrue();
	}

	@Test
	@DisplayName("Scenario: Senha de usuário inexistente")
	void shouldReturnNotFoundWhenChangingPasswordOfMissingUser() throws Exception {
		// when
		ResultActions result = putPassword(UUID.fromString(MISSING_ID), """
				{"password": "novaSenha456"}""");

		// then
		result.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	// ---------- Excluir usuário ----------

	@Test
	@DisplayName("Scenario: Usuário excluído")
	void shouldDeleteUserWhenIdExists() throws Exception {
		// given
		UUID id = createAna();

		// when
		ResultActions result = performAsZelia(delete(USERS + "/{id}", id));

		// then
		result.andExpect(status().isNoContent());
		performAsZelia(get(USERS + "/{id}", id))
				.andExpect(status().isNotFound());
		performAsZelia(get(USERS))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id").value(not(hasItem(id.toString()))))
				.andExpect(jsonPath("$.content[*].login").value(contains(ZELIA_LOGIN)))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	@DisplayName("Scenario: Login liberado após exclusão")
	void shouldAllowLoginReuseWhenPreviousOwnerWasDeleted() throws Exception {
		// given
		UUID id = createAna();
		performAsZelia(delete(USERS + "/{id}", id)).andExpect(status().isNoContent());

		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza Nova", "login": "ana.souza@educare.org", "password": "segredo123"}""");

		// then
		result.andExpect(status().isCreated());
		assertThat(countUsersWithLogin(ANA_LOGIN)).isEqualTo(1);
	}

	@Test
	@DisplayName("Scenario: Exclusão de usuário inexistente")
	void shouldReturnNotFoundWhenDeletingMissingUser() throws Exception {
		// when
		ResultActions result = performAsZelia(delete(USERS + "/" + MISSING_ID));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	// ---------- Corpo de requisição ilegível ----------

	@Test
	@DisplayName("Scenario: JSON inválido na criação")
	void shouldRejectCreationWhenJsonIsMalformed() throws Exception {
		// when
		ResultActions result = postUser("{\"name\": \"Ana\"");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
		assertThat(countUsersBesidesZelia()).isZero();
	}

	@Test
	@DisplayName("Scenario: JSON válido é aceito")
	void shouldCreateUserWhenJsonIsWellFormed() throws Exception {
		// when
		ResultActions result = postUser("""
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123"}""");

		// then
		result.andExpect(status().isCreated());
		assertThat(countUsersBesidesZelia()).isEqualTo(1);
	}

	// ---------- Gestão de usuários restrita a ADMIN ----------

	@Test
	@DisplayName("Scenario: ADMIN lista usuários")
	void shouldListUsersWhenRequesterIsAdmin() throws Exception {
		// given
		testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "ADMIN");
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].login").value(hasItem(ADMIN_LOGIN)));
	}

	@Test
	@DisplayName("Scenario: USER não cria usuário")
	void shouldRejectCreationWhenRequesterIsUser() throws Exception {
		// given
		testUsers.create("Ana Souza", ANA_LOGIN, PASSWORD, "USER");
		String anaBearer = testUsers.bearer(ANA_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(json(post(USERS), """
				{"name": "Carla", "login": "carla@educare.org", "password": "segredo123", "role": "ADMIN"}""")
				.header(HttpHeaders.AUTHORIZATION, anaBearer));

		// then
		result.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
		assertThat(countUsersWithLogin("carla@educare.org")).isZero();
	}

	@Test
	@DisplayName("Scenario: USER não se promove")
	void shouldRejectSelfPromotionWhenRequesterIsUser() throws Exception {
		// given
		UUID anaId = testUsers.create("Ana Souza", ANA_LOGIN, PASSWORD, "USER");
		String anaBearer = testUsers.bearer(ANA_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(json(put(USERS + "/{id}", anaId), """
				{"name": "Ana Souza", "login": "ana.souza@educare.org", "role": "ADMIN"}""")
				.header(HttpHeaders.AUTHORIZATION, anaBearer));

		// then
		result.andExpect(status().isForbidden());
		assertThat(roleInDatabase(anaId)).isEqualTo("USER");
	}

	@Test
	@DisplayName("Scenario: Exclusão sem token")
	void shouldRejectDeletionWhenAuthorizationHeaderIsMissing() throws Exception {
		// given
		UUID anaId = testUsers.create("Ana Souza", ANA_LOGIN, PASSWORD, "USER");

		// when
		ResultActions result = mockMvc.perform(delete(USERS + "/{id}", anaId));

		// then
		result.andExpect(status().isUnauthorized());
		assertThat(countUsersWithLogin(ANA_LOGIN)).isEqualTo(1);
	}

	// ---------- Sistema nunca fica sem ADMIN ----------
	// These scenarios remove Zélia first, so the only ADMINs are exactly the ones named in the spec.

	@Test
	@DisplayName("Scenario: ADMIN tenta se excluir")
	void shouldRejectDeletionWhenAdminDeletesThemself() throws Exception {
		// given
		removeZelia();
		UUID adminId = testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "ADMIN");
		testUsers.create("Bruno Lima", BRUNO_LOGIN, PASSWORD, "ADMIN");
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(delete(USERS + "/{id}", adminId)
				.header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
		assertThat(countUsersWithLogin(ADMIN_LOGIN)).isEqualTo(1);
	}

	@Test
	@DisplayName("Scenario: Último ADMIN tenta se rebaixar")
	void shouldRejectDemotionWhenAdminIsTheLastOne() throws Exception {
		// given
		removeZelia();
		UUID adminId = testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "ADMIN");
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(json(put(USERS + "/{id}", adminId), """
				{"name": "Administrador", "login": "admin@educare.org", "role": "USER"}""")
				.header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isConflict());
		assertThat(roleInDatabase(adminId)).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("Scenario: ADMIN se rebaixa havendo outro ADMIN")
	void shouldDemoteAdminWhenAnotherAdminRemains() throws Exception {
		// given
		removeZelia();
		UUID adminId = testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "ADMIN");
		testUsers.create("Bruno Lima", BRUNO_LOGIN, PASSWORD, "ADMIN");
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(json(put(USERS + "/{id}", adminId), """
				{"name": "Administrador", "login": "admin@educare.org", "role": "USER"}""")
				.header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("USER"));
		assertThat(roleInDatabase(adminId)).isEqualTo("USER");
	}

	@Test
	@DisplayName("Scenario: ADMIN exclui outro ADMIN")
	void shouldDeleteAnotherAdminWhenAdminRemains() throws Exception {
		// given
		removeZelia();
		UUID adminId = testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "ADMIN");
		UUID brunoId = testUsers.create("Bruno Lima", BRUNO_LOGIN, PASSWORD, "ADMIN");
		String adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(delete(USERS + "/{id}", brunoId)
				.header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isNoContent());
		assertThat(countUsersWithLogin(BRUNO_LOGIN)).isZero();
		assertThat(roleInDatabase(adminId)).isEqualTo("ADMIN");
	}

	// ---------- helpers ----------

	private ResultActions performAsZelia(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, zeliaBearer));
	}

	private void removeZelia() {
		jdbcTemplate.update("DELETE FROM users WHERE id = ?", zeliaId);
	}

	private ResultActions postUser(String body) throws Exception {
		return performAsZelia(json(post(USERS), body));
	}

	private ResultActions putUser(UUID id, String body) throws Exception {
		return performAsZelia(json(put(USERS + "/{id}", id), body));
	}

	private ResultActions putPassword(UUID id, String body) throws Exception {
		return performAsZelia(json(put(USERS + "/{id}/password", id), body));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private UUID createAna() throws Exception {
		return createUser("Ana Souza", ANA_LOGIN, "segredo123", null);
	}

	private UUID createUser(String name, String login, String password, String role) throws Exception {
		String roleProperty = role == null ? "" : ", \"role\": \"%s\"".formatted(role);
		String body = """
				{"name": "%s", "login": "%s", "password": "%s"%s}""".formatted(name, login, password, roleProperty);
		return idOf(postUser(body).andExpect(status().isCreated()).andReturn());
	}

	private static UUID idOf(MvcResult result) throws Exception {
		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		return UUID.fromString(id);
	}

	private static void assertValidationError(ResultActions result, String field) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem(field)));
	}

	private int countUsersBesidesZelia() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id <> ?", Integer.class, zeliaId);
	}

	private int countUsersWithLogin(String login) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE lower(login) = lower(?)",
				Integer.class, login);
	}

	private String passwordHashInDatabase(UUID id) {
		return columnInDatabase("password_hash", id);
	}

	private String nameInDatabase(UUID id) {
		return columnInDatabase("name", id);
	}

	private String loginInDatabase(UUID id) {
		return columnInDatabase("login", id);
	}

	private String roleInDatabase(UUID id) {
		return columnInDatabase("role", id);
	}

	private String columnInDatabase(String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM users WHERE id = ?", String.class, id);
	}

}
