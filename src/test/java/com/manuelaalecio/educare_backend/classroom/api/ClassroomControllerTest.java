package com.manuelaalecio.educare_backend.classroom.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.classroom.application.ClassroomOverview;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomService;
import com.manuelaalecio.educare_backend.classroom.application.StudentCounts;
import com.manuelaalecio.educare_backend.classroom.domain.Classroom;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomHasStudentsException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomNameAlreadyInUseException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomNotFoundException;
import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.error.GlobalExceptionHandler;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUserLookup;
import com.manuelaalecio.educare_backend.shared.security.JwtConfiguration;
import com.manuelaalecio.educare_backend.shared.security.ProblemDetailSecurityHandlers;
import com.manuelaalecio.educare_backend.shared.security.SecurityConfiguration;
import com.manuelaalecio.educare_backend.shared.security.TokenOwner;
import com.manuelaalecio.educare_backend.shared.testsupport.AccessTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ClassroomController.class)
@Import({ClassroomMapper.class, SecurityConfiguration.class, JwtConfiguration.class, ClockConfiguration.class,
		ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class, AccessTokens.class})
class ClassroomControllerTest {

	private static final String CLASSROOMS = "/api/v1/classrooms";
	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID OTHER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");
	private static final UUID ADMIN_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000010");
	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000020");
	private static final Instant CREATED_AT = Instant.parse("2026-11-03T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-11-04T09:00:00Z");
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String VALID_BODY = """
			{"name": "Turma 1"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccessTokens accessTokens;

	@MockitoBean
	private ClassroomService classroomService;

	@MockitoBean
	private AuthenticatedUserLookup lookup;

	@BeforeEach
	void setUpAuthenticatedUsers() {
		given(lookup.findById(ADMIN_ID))
				.willReturn(Optional.of(new TokenOwner(new AuthenticatedUser(ADMIN_ID, "ADMIN"), null)));
		given(lookup.findById(USER_ID))
				.willReturn(Optional.of(new TokenOwner(new AuthenticatedUser(USER_ID, "USER"), null)));
	}

	// ---------- authentication and authorization (every endpoint) ----------

	static Stream<Arguments> endpoints() {
		return Stream.of(
				Arguments.of("create", (Supplier<MockHttpServletRequestBuilder>) () ->
						postJson(CLASSROOMS, VALID_BODY)),
				Arguments.of("list", (Supplier<MockHttpServletRequestBuilder>) () -> get(CLASSROOMS)),
				Arguments.of("findById", (Supplier<MockHttpServletRequestBuilder>) () ->
						get(CLASSROOMS + "/{id}", ID)),
				Arguments.of("rename", (Supplier<MockHttpServletRequestBuilder>) () ->
						putJson(CLASSROOMS + "/" + ID, VALID_BODY)),
				Arguments.of("delete", (Supplier<MockHttpServletRequestBuilder>) () ->
						delete(CLASSROOMS + "/{id}", ID)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("endpoints")
	void shouldRespondUnauthorizedProblemWhenTokenIsMissing(String endpoint,
			Supplier<MockHttpServletRequestBuilder> request) throws Exception {
		// when
		ResultActions result = mockMvc.perform(request.get());

		// then
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401));
		then(classroomService).shouldHaveNoInteractions();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("endpoints")
	void shouldRespondForbiddenProblemWithoutCallingServiceWhenUserIsNotAdmin(String endpoint,
			Supplier<MockHttpServletRequestBuilder> request) throws Exception {
		// when
		ResultActions result = mockMvc.perform(request.get()
				.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(USER_ID)));

		// then
		result.andExpect(status().isForbidden())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));
		then(classroomService).shouldHaveNoInteractions();
	}

	// ---------- POST /api/v1/classrooms ----------

	@Test
	void shouldCreateClassroomAndReturnLocationWhenBodyIsValid() throws Exception {
		// given
		given(classroomService.create("Turma 1")).willReturn(overview(ID, "Turma 1", StudentCounts.ZERO));

		// when
		ResultActions result = performAsAdmin(postJson(CLASSROOMS, VALID_BODY));

		// then
		result.andExpect(status().isCreated())
				.andExpect(header().string("Location", CLASSROOMS + "/" + ID))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.name").value("Turma 1"))
				.andExpect(jsonPath("$.studentCount").value(0))
				.andExpect(jsonPath("$.maleCount").value(0))
				.andExpect(jsonPath("$.femaleCount").value(0))
				.andExpect(jsonPath("$.createdAt").value("2026-11-03T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-11-04T09:00:00Z"))
				.andExpect(jsonPath("$.createdBy").doesNotExist())
				.andExpect(jsonPath("$.updatedBy").doesNotExist());
	}

	@Test
	void shouldPassStrippedNameToServiceWhenNameHasSurroundingSpaces() throws Exception {
		// given
		given(classroomService.create("Turma 3")).willReturn(overview(ID, "Turma 3", StudentCounts.ZERO));

		// when
		ResultActions result = performAsAdmin(postJson(CLASSROOMS, """
				{"name": "  Turma 3 "}"""));

		// then
		result.andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Turma 3"));
		then(classroomService).should().create("Turma 3");
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"name\": \"\"}", "{\"name\": \"   \"}", "{\"name\": null}"})
	void shouldRejectCreationWhenNameIsMissingOrBlank(String body) throws Exception {
		// when
		ResultActions result = performAsAdmin(postJson(CLASSROOMS, body));

		// then
		expectNameError(result, "é obrigatório");
	}

	@Test
	void shouldRejectCreationWhenNameIsLongerThanMaxLength() throws Exception {
		// when
		ResultActions result = performAsAdmin(postJson(CLASSROOMS, nameBody("a".repeat(61))));

		// then
		expectNameError(result, "deve ter no máximo 60 caracteres");
	}

	@Test
	void shouldRejectCreationWithoutErrorsWhenJsonIsUnreadable() throws Exception {
		// when
		ResultActions result = performAsAdmin(postJson(CLASSROOMS, """
				{"name": "Turma 1\""""));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors").doesNotExist());
		then(classroomService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondConflictWithoutNameWhenCreatingWithNameInUse() throws Exception {
		// given
		given(classroomService.create("turma 1")).willThrow(new ClassroomNameAlreadyInUseException());

		// when
		ResultActions result = performAsAdmin(postJson(CLASSROOMS, nameBody("turma 1")));

		// then
		expectConflict(result, "Já existe uma turma com esse nome");
		result.andExpect(content().string(not(containsString("turma 1"))));
	}

	// ---------- GET /api/v1/classrooms ----------

	@Test
	void shouldListClassroomsInServiceOrderWhenListing() throws Exception {
		// given
		given(classroomService.list()).willReturn(List.of(
				overview(ID, "Turma 1", new StudentCounts(3, 1, 1)),
				overview(OTHER_ID, "Turma 2", StudentCounts.ZERO)));

		// when
		ResultActions result = performAsAdmin(get(CLASSROOMS));

		// then
		result.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(ID.toString()))
				.andExpect(jsonPath("$[0].name").value("Turma 1"))
				.andExpect(jsonPath("$[0].studentCount").value(3))
				.andExpect(jsonPath("$[0].maleCount").value(1))
				.andExpect(jsonPath("$[0].femaleCount").value(1))
				.andExpect(jsonPath("$[0].createdAt").value("2026-11-03T10:00:00Z"))
				.andExpect(jsonPath("$[1].name").value("Turma 2"))
				.andExpect(jsonPath("$[1].studentCount").value(0))
				.andExpect(jsonPath("$[0].createdBy").doesNotExist());
	}

	@Test
	void shouldReturnEmptyArrayWhenThereAreNoClassrooms() throws Exception {
		// given
		given(classroomService.list()).willReturn(List.of());

		// when
		ResultActions result = performAsAdmin(get(CLASSROOMS));

		// then
		result.andExpect(status().isOk()).andExpect(content().string("[]"));
	}

	// ---------- GET /api/v1/classrooms/{id} ----------

	@Test
	void shouldReturnClassroomWhenItExists() throws Exception {
		// given
		given(classroomService.findById(ID)).willReturn(overview(ID, "Maternal A", new StudentCounts(2, 1, 0)));

		// when
		ResultActions result = performAsAdmin(get(CLASSROOMS + "/{id}", ID));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.name").value("Maternal A"))
				.andExpect(jsonPath("$.studentCount").value(2))
				.andExpect(jsonPath("$.maleCount").value(1))
				.andExpect(jsonPath("$.femaleCount").value(0));
	}

	@Test
	void shouldRespondNotFoundWhenClassroomDoesNotExist() throws Exception {
		// given
		given(classroomService.findById(ID)).willThrow(new ClassroomNotFoundException());

		// when
		ResultActions result = performAsAdmin(get(CLASSROOMS + "/{id}", ID));

		// then
		expectNotFound(result);
	}

	@Test
	void shouldRespondBadRequestWhenIdIsMalformed() throws Exception {
		// when
		ResultActions result = performAsAdmin(get(CLASSROOMS + "/abc"));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(classroomService).shouldHaveNoInteractions();
	}

	// ---------- PUT /api/v1/classrooms/{id} ----------

	@Test
	void shouldRenameClassroomWhenBodyIsValid() throws Exception {
		// given
		given(classroomService.rename(ID, "Maternal A")).willReturn(overview(ID, "Maternal A", StudentCounts.ZERO));

		// when
		ResultActions result = performAsAdmin(putJson(CLASSROOMS + "/" + ID, nameBody(" Maternal A ")));

		// then
		result.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.name").value("Maternal A"))
				.andExpect(jsonPath("$.createdAt").value("2026-11-03T10:00:00Z"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"name\": \"\"}", "{\"name\": \"   \"}"})
	void shouldRejectRenameWhenNameIsMissingOrBlank(String body) throws Exception {
		// when
		ResultActions result = performAsAdmin(putJson(CLASSROOMS + "/" + ID, body));

		// then
		expectNameError(result, "é obrigatório");
	}

	@Test
	void shouldRejectRenameWhenNameIsLongerThanMaxLength() throws Exception {
		// when
		ResultActions result = performAsAdmin(putJson(CLASSROOMS + "/" + ID, nameBody("a".repeat(61))));

		// then
		expectNameError(result, "deve ter no máximo 60 caracteres");
	}

	@Test
	void shouldRejectRenameWithoutErrorsWhenJsonIsUnreadable() throws Exception {
		// when
		ResultActions result = performAsAdmin(putJson(CLASSROOMS + "/" + ID, "{\"name\":"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors").doesNotExist());
		then(classroomService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondNotFoundWhenRenamingMissingClassroom() throws Exception {
		// given
		given(classroomService.rename(ID, "Maternal A")).willThrow(new ClassroomNotFoundException());

		// when
		ResultActions result = performAsAdmin(putJson(CLASSROOMS + "/" + ID, nameBody("Maternal A")));

		// then
		expectNotFound(result);
	}

	@Test
	void shouldRespondConflictWhenRenamingToNameInUse() throws Exception {
		// given
		given(classroomService.rename(ID, "TURMA 1")).willThrow(new ClassroomNameAlreadyInUseException());

		// when
		ResultActions result = performAsAdmin(putJson(CLASSROOMS + "/" + ID, nameBody("TURMA 1")));

		// then
		expectConflict(result, "Já existe uma turma com esse nome");
		result.andExpect(content().string(not(containsString("TURMA 1"))));
	}

	// ---------- DELETE /api/v1/classrooms/{id} ----------

	@Test
	void shouldRespondNoContentWhenDeletingClassroom() throws Exception {
		// when
		ResultActions result = performAsAdmin(delete(CLASSROOMS + "/{id}", ID));

		// then
		result.andExpect(status().isNoContent()).andExpect(content().string(""));
		then(classroomService).should().delete(ID);
	}

	@Test
	void shouldRespondNotFoundWhenDeletingMissingClassroom() throws Exception {
		// given
		willThrow(new ClassroomNotFoundException()).given(classroomService).delete(ID);

		// when
		ResultActions result = performAsAdmin(delete(CLASSROOMS + "/{id}", ID));

		// then
		expectNotFound(result);
	}

	@Test
	void shouldRespondConflictWhenDeletingClassroomWithStudents() throws Exception {
		// given
		willThrow(new ClassroomHasStudentsException()).given(classroomService).delete(ID);

		// when
		ResultActions result = performAsAdmin(delete(CLASSROOMS + "/{id}", ID));

		// then
		expectConflict(result, "A turma tem alunos. Tire os alunos da turma antes de excluí-la");
	}

	@Test
	void shouldRespondBadRequestWhenDeletingWithMalformedId() throws Exception {
		// when
		ResultActions result = performAsAdmin(delete(CLASSROOMS + "/abc"));

		// then
		result.andExpect(status().isBadRequest()).andExpect(content().contentType(PROBLEM_JSON));
		then(classroomService).shouldHaveNoInteractions();
	}

	private ResultActions performAsAdmin(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(ADMIN_ID)));
	}

	private void expectNameError(ResultActions result, String message) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.length()").value(1))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value(message));
		then(classroomService).shouldHaveNoInteractions();
	}

	private static void expectNotFound(ResultActions result) throws Exception {
		result.andExpect(status().isNotFound())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Turma não encontrada"));
	}

	private static void expectConflict(ResultActions result, String detail) throws Exception {
		result.andExpect(status().isConflict())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value(detail));
	}

	private static ClassroomOverview overview(UUID id, String name, StudentCounts counts) {
		var classroom = new Classroom(name);
		ReflectionTestUtils.setField(classroom, "id", id);
		ReflectionTestUtils.setField(classroom, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(classroom, "updatedAt", UPDATED_AT);
		return new ClassroomOverview(classroom, counts);
	}

	private static String nameBody(String name) {
		return "{\"name\": \"" + name + "\"}";
	}

	private static MockHttpServletRequestBuilder postJson(String uri, String body) {
		return post(uri).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockHttpServletRequestBuilder putJson(String uri, String body) {
		return put(uri).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
