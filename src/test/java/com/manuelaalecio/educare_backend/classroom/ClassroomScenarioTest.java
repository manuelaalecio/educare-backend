package com.manuelaalecio.educare_backend.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * One test per Scenario of the {@code classroom} spec, end to end through the API with a real database. The two
 * scenarios that need students are in {@link ClassroomStudentCountScenarioTest}, because classes have no students
 * until enrollment exists. Users and classes are recreated before every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class})
class ClassroomScenarioTest {

	private static final String CLASSROOMS = "/api/v1/classrooms";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String MISSING_ID = "0190f4a2-0000-7000-8000-000000000000";
	private static final Instant CREATED_AT = Instant.parse("2026-11-03T10:00:00Z");
	private static final Instant RENAMED_AT = Instant.parse("2026-11-04T09:00:00Z");
	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String BRUNO_LOGIN = "bruno@educare.org";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String PASSWORD = "segredo123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@Autowired
	private TestUsers testUsers;

	private UUID adminId;
	private UUID brunoId;
	private String adminBearer;
	private String anaBearer;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("DELETE FROM classrooms");
		jdbcTemplate.update("DELETE FROM users");
		clock.setInstant(CREATED_AT);
		adminId = testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "ADMIN");
		brunoId = testUsers.create("Bruno", BRUNO_LOGIN, PASSWORD, "ADMIN");
		testUsers.create("Ana Souza", ANA_LOGIN, PASSWORD, "USER");
		adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);
		anaBearer = testUsers.bearer(ANA_LOGIN, PASSWORD);
	}

	// ---------- Criar turma ----------

	@Test
	@DisplayName("Scenario: Turma criada com nome válido")
	void shouldCreateClassroomWhenNameIsValid() throws Exception {
		// when
		ResultActions result = postClassroom("""
				{"name": "Turma 1"}""");

		// then
		MvcResult mvcResult = result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Turma 1"))
				.andExpect(jsonPath("$.studentCount").value(0))
				.andExpect(jsonPath("$.maleCount").value(0))
				.andExpect(jsonPath("$.femaleCount").value(0))
				.andExpect(jsonPath("$.createdAt").value("2026-11-03T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-11-03T10:00:00Z"))
				.andReturn();
		UUID id = idOf(mvcResult);
		assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(CLASSROOMS + "/" + id);
		assertThat(countClassrooms()).isEqualTo(1);
	}

	@Test
	@DisplayName("Scenario: Nome com espaços nas pontas é gravado sem eles")
	void shouldStripNameWhenNameHasSurroundingSpaces() throws Exception {
		// when
		ResultActions result = postClassroom("""
				{"name": "  Turma 3 "}""");

		// then
		UUID id = idOf(result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Turma 3"))
				.andReturn());
		getClassroom(id, adminBearer).andExpect(jsonPath("$.name").value("Turma 3"));
	}

	@Test
	@DisplayName("Scenario: Nome com 60 caracteres sem contar os espaços das pontas")
	void shouldAcceptNameWhenItHasMaxLengthWithoutSurroundingSpaces() throws Exception {
		// when
		ResultActions result = postClassroom(nameBody(" " + "a".repeat(60) + " "));

		// then
		UUID id = idOf(result.andExpect(status().isCreated()).andReturn());
		assertThat(nameInDatabase(id)).isEqualTo("a".repeat(60));
	}

	@Test
	@DisplayName("Scenario: Nome ausente")
	void shouldRejectCreationWhenNameIsMissing() throws Exception {
		// when
		ResultActions result = postClassroom("{}");

		// then
		expectNameError(result);
		assertThat(countClassrooms()).isZero();
	}

	@Test
	@DisplayName("Scenario: Nome só com espaços")
	void shouldRejectCreationWhenNameIsOnlySpaces() throws Exception {
		// when
		ResultActions result = postClassroom("""
				{"name": "   "}""");

		// then
		expectNameError(result);
		assertThat(countClassrooms()).isZero();
	}

	@Test
	@DisplayName("Scenario: Nome longo demais")
	void shouldRejectCreationWhenNameIsTooLong() throws Exception {
		// when
		ResultActions result = postClassroom(nameBody("a".repeat(61)));

		// then
		expectNameError(result);
		assertThat(countClassrooms()).isZero();
	}

	// ---------- Nome de turma único sem diferenciar maiúsculas e acentos ----------

	@Test
	@DisplayName("Scenario: Criação com nome já usado em outra caixa")
	void shouldRejectCreationWhenNameIsUsedInAnotherCase() throws Exception {
		// given
		createClassroom("Turma 1");

		// when
		ResultActions result = postClassroom("""
				{"name": "turma 1"}""");

		// then
		result.andExpect(status().isConflict()).andExpect(content().contentType(PROBLEM_JSON));
		assertThat(countClassrooms()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT name FROM classrooms", String.class)).isEqualTo("Turma 1");
	}

	@Test
	@DisplayName("Scenario: Criação com nome já usado com acento")
	void shouldRejectCreationWhenNameIsUsedWithAccent() throws Exception {
		// given
		createClassroom("Turma 1");

		// when
		ResultActions result = postClassroom("""
				{"name": "Túrma 1"}""");

		// then
		result.andExpect(status().isConflict());
		assertThat(countClassrooms()).isEqualTo(1);
	}

	@Test
	@DisplayName("Scenario: Renomeação para o nome de outra turma")
	void shouldRejectRenameWhenNameBelongsToAnotherClassroom() throws Exception {
		// given
		createClassroom("Turma 1");
		UUID turma2 = createClassroom("Turma 2");

		// when
		ResultActions result = putClassroom(turma2, """
				{"name": "TURMA 1"}""");

		// then
		result.andExpect(status().isConflict());
		assertThat(nameInDatabase(turma2)).isEqualTo("Turma 2");
	}

	@Test
	@DisplayName("Scenario: Renomeação para uma variação do próprio nome")
	void shouldRenameWhenNewNameIsVariationOfOwnName() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");

		// when
		ResultActions result = putClassroom(id, """
				{"name": "TURMA 1"}""");

		// then
		result.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("TURMA 1"));
	}

	@Test
	@DisplayName("Scenario: Nome livre depois da exclusão")
	void shouldAcceptNameWhenClassroomWithItWasDeleted() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");
		deleteClassroom(id, adminBearer).andExpect(status().isNoContent());

		// when
		ResultActions result = postClassroom("""
				{"name": "Turma 1"}""");

		// then
		result.andExpect(status().isCreated());
	}

	// ---------- Consultar turma por id ----------

	@Test
	@DisplayName("Scenario: Turma existente")
	void shouldReturnClassroomWhenItExists() throws Exception {
		// given
		UUID id = createClassroom("Maternal A");

		// when
		ResultActions result = getClassroom(id, adminBearer);

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.name").value("Maternal A"))
				.andExpect(jsonPath("$.studentCount").value(0))
				.andExpect(jsonPath("$.maleCount").value(0))
				.andExpect(jsonPath("$.femaleCount").value(0))
				.andExpect(jsonPath("$.createdAt").value("2026-11-03T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-11-03T10:00:00Z"));
	}

	@Test
	@DisplayName("Scenario: Turma inexistente")
	void shouldRespondNotFoundWhenClassroomDoesNotExist() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(CLASSROOMS + "/" + MISSING_ID)
				.header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isNotFound()).andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Scenario: Id malformado")
	void shouldRespondBadRequestWhenIdIsMalformed() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get(CLASSROOMS + "/abc").header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Listar turmas em ordem alfabética ----------

	@Test
	@DisplayName("Scenario: Turmas em ordem alfabética")
	void shouldListClassroomsAlphabeticallyIgnoringCase() throws Exception {
		// given
		createClassroom("Turma 2");
		createClassroom("berçário");
		createClassroom("Turma 1");

		// when
		ResultActions result = listClassrooms(adminBearer);

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[*].name").value(contains("berçário", "Turma 1", "Turma 2")))
				.andExpect(jsonPath("$[*].studentCount").value(contains(0, 0, 0)))
				.andExpect(jsonPath("$[*].maleCount").value(contains(0, 0, 0)))
				.andExpect(jsonPath("$[*].femaleCount").value(contains(0, 0, 0)))
				.andExpect(jsonPath("$[*].createdAt").value(contains("2026-11-03T10:00:00Z",
						"2026-11-03T10:00:00Z", "2026-11-03T10:00:00Z")));
	}

	@Test
	@DisplayName("Scenario: Acento não muda a posição")
	void shouldListClassroomsAlphabeticallyIgnoringAccents() throws Exception {
		// given
		createClassroom("Educação Infantil");
		createClassroom("Évora");
		createClassroom("Ensino");

		// when
		ResultActions result = listClassrooms(adminBearer);

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name").value(contains("Educação Infantil", "Ensino", "Évora")));
	}

	@Test
	@DisplayName("Scenario: Nenhuma turma cadastrada")
	void shouldReturnEmptyArrayWhenThereAreNoClassrooms() throws Exception {
		// when
		ResultActions result = listClassrooms(adminBearer);

		// then
		result.andExpect(status().isOk()).andExpect(content().string("[]"));
	}

	// ---------- Renomear turma ----------

	@Test
	@DisplayName("Scenario: Turma renomeada")
	void shouldRenameClassroomKeepingCreationDate() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");
		clock.setInstant(RENAMED_AT);
		// the token of the setup expired in the meantime (8 hours), so the admin logs in again
		String bearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);

		// when
		ResultActions result = mockMvc.perform(withJson(put(CLASSROOMS + "/" + id), """
				{"name": "Maternal A"}""").header(HttpHeaders.AUTHORIZATION, bearer));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Maternal A"))
				.andExpect(jsonPath("$.createdAt").value("2026-11-03T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-11-04T09:00:00Z"));
		getClassroom(id, bearer).andExpect(jsonPath("$.name").value("Maternal A"));
	}

	@Test
	@DisplayName("Scenario: Renomeação com nome inválido")
	void shouldKeepNameWhenRenamingToInvalidName() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");

		// when
		ResultActions result = putClassroom(id, """
				{"name": ""}""");

		// then
		expectNameError(result);
		assertThat(nameInDatabase(id)).isEqualTo("Turma 1");
	}

	@Test
	@DisplayName("Scenario: Renomeação de turma inexistente")
	void shouldRespondNotFoundWhenRenamingMissingClassroom() throws Exception {
		// when
		ResultActions result = putClassroom(UUID.fromString(MISSING_ID), """
				{"name": "Maternal A"}""");

		// then
		result.andExpect(status().isNotFound()).andExpect(content().contentType(PROBLEM_JSON));
		assertThat(countClassrooms()).isZero();
	}

	// ---------- Excluir turma ----------

	@Test
	@DisplayName("Scenario: Turma sem alunos excluída")
	void shouldDeleteClassroomWithoutStudents() throws Exception {
		// given
		UUID id = createClassroom("Turma 3");

		// when
		ResultActions result = deleteClassroom(id, adminBearer);

		// then
		result.andExpect(status().isNoContent()).andExpect(content().string(""));
		getClassroom(id, adminBearer).andExpect(status().isNotFound());
		listClassrooms(adminBearer).andExpect(jsonPath("$[*].name").value(not(hasItem("Turma 3"))));
	}

	@Test
	@DisplayName("Scenario: Exclusão de turma inexistente")
	void shouldRespondNotFoundWhenDeletingMissingClassroom() throws Exception {
		// when
		ResultActions result = deleteClassroom(UUID.fromString(MISSING_ID), adminBearer);

		// then
		result.andExpect(status().isNotFound()).andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Contagens de alunos da turma ----------

	@Test
	@DisplayName("Scenario: Turma nova sem alunos")
	void shouldCountZeroStudentsWhenClassroomIsNew() throws Exception {
		// given
		createClassroom("Turma 1");

		// when
		ResultActions result = listClassrooms(adminBearer);

		// then
		result.andExpect(jsonPath("$[0].name").value("Turma 1"))
				.andExpect(jsonPath("$[0].studentCount").value(0))
				.andExpect(jsonPath("$[0].maleCount").value(0))
				.andExpect(jsonPath("$[0].femaleCount").value(0));
	}

	// ---------- Autoria da turma ----------

	@Test
	@DisplayName("Scenario: Autoria de criação e de renomeação")
	void shouldRecordCreatorAndLastEditorWhenClassroomIsCreatedAndRenamed() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");
		String brunoBearer = testUsers.bearer(BRUNO_LOGIN, PASSWORD);

		// when
		mockMvc.perform(withJson(put(CLASSROOMS + "/" + id), """
				{"name": "Maternal A"}""").header(HttpHeaders.AUTHORIZATION, brunoBearer))
				.andExpect(status().isOk());

		// then
		assertThat(authorInDatabase("created_by", id)).isEqualTo(adminId);
		assertThat(authorInDatabase("updated_by", id)).isEqualTo(brunoId);
	}

	@Test
	@DisplayName("Scenario: Autoria fora da resposta")
	void shouldNotExposeAuthorsWhenClassroomIsCreated() throws Exception {
		// when
		ResultActions result = postClassroom("""
				{"name": "Turma 1"}""");

		// then
		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.createdBy").doesNotExist())
				.andExpect(jsonPath("$.updatedBy").doesNotExist());
	}

	// ---------- Corpo de requisição ilegível ----------

	@Test
	@DisplayName("Scenario: JSON inválido na criação")
	void shouldRejectCreationWhenJsonIsIncomplete() throws Exception {
		// when
		ResultActions result = postClassroom("""
				{"name": "Turma 1\"""");

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		assertThat(countClassrooms()).isZero();
	}

	@Test
	@DisplayName("Scenario: JSON válido é aceito")
	void shouldAcceptCreationWhenJsonIsWellFormed() throws Exception {
		// when
		ResultActions result = postClassroom("""
				{"name": "Turma 1"}""");

		// then
		result.andExpect(status().isCreated());
	}

	// ---------- Gestão de turmas restrita a ADMIN ----------

	@Test
	@DisplayName("Scenario: ADMIN lista turmas")
	void shouldListClassroomsWhenUserIsAdmin() throws Exception {
		// when
		ResultActions result = listClassrooms(adminBearer);

		// then
		result.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Scenario: USER não lista turmas")
	void shouldForbidListingWhenUserIsNotAdmin() throws Exception {
		// when
		ResultActions result = listClassrooms(anaBearer);

		// then
		result.andExpect(status().isForbidden()).andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Scenario: USER não cria turma")
	void shouldForbidCreationWhenUserIsNotAdmin() throws Exception {
		// when
		ResultActions result = mockMvc.perform(withJson(post(CLASSROOMS), """
				{"name": "Turma 9"}""").header(HttpHeaders.AUTHORIZATION, anaBearer));

		// then
		result.andExpect(status().isForbidden());
		assertThat(countClassrooms()).isZero();
	}

	@Test
	@DisplayName("Scenario: USER não renomeia nem exclui turma")
	void shouldForbidReadingRenamingAndDeletionWhenUserIsNotAdmin() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");

		// when
		ResultActions read = getClassroom(id, anaBearer);
		ResultActions rename = mockMvc.perform(withJson(put(CLASSROOMS + "/" + id), """
				{"name": "Maternal A"}""").header(HttpHeaders.AUTHORIZATION, anaBearer));
		ResultActions deletion = deleteClassroom(id, anaBearer);

		// then
		read.andExpect(status().isForbidden());
		rename.andExpect(status().isForbidden());
		deletion.andExpect(status().isForbidden());
		assertThat(nameInDatabase(id)).isEqualTo("Turma 1");
	}

	@Test
	@DisplayName("Scenario: Exclusão sem token")
	void shouldRespondUnauthorizedWhenDeletingWithoutToken() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");

		// when
		ResultActions result = mockMvc.perform(delete(CLASSROOMS + "/" + id));

		// then
		result.andExpect(status().isUnauthorized());
		assertThat(countClassrooms()).isEqualTo(1);
	}

	// ---------- helpers ----------

	private UUID createClassroom(String name) throws Exception {
		return idOf(postClassroom(nameBody(name)).andExpect(status().isCreated()).andReturn());
	}

	private ResultActions postClassroom(String body) throws Exception {
		return mockMvc.perform(withJson(post(CLASSROOMS), body).header(HttpHeaders.AUTHORIZATION, adminBearer));
	}

	private ResultActions putClassroom(UUID id, String body) throws Exception {
		return mockMvc.perform(withJson(put(CLASSROOMS + "/" + id), body)
				.header(HttpHeaders.AUTHORIZATION, adminBearer));
	}

	private ResultActions getClassroom(UUID id, String bearer) throws Exception {
		return mockMvc.perform(get(CLASSROOMS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private ResultActions listClassrooms(String bearer) throws Exception {
		return mockMvc.perform(get(CLASSROOMS).header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private ResultActions deleteClassroom(UUID id, String bearer) throws Exception {
		return mockMvc.perform(delete(CLASSROOMS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private static void expectNameError(ResultActions result) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors[*].field").value(contains("name")));
	}

	private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String nameBody(String name) {
		return "{\"name\": \"" + name + "\"}";
	}

	private static UUID idOf(MvcResult result) throws Exception {
		return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
	}

	private int countClassrooms() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM classrooms", Integer.class);
	}

	private String nameInDatabase(UUID id) {
		return jdbcTemplate.queryForObject("SELECT name FROM classrooms WHERE id = ?", String.class, id);
	}

	private UUID authorInDatabase(String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM classrooms WHERE id = ?", UUID.class, id);
	}

}
