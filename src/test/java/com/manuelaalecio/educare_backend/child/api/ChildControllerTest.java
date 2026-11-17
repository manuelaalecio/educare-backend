package com.manuelaalecio.educare_backend.child.api;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.BIRTH_CERTIFICATE_NUMBER;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aCompleteChild;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.child.ChildDetailsBuilder;
import com.manuelaalecio.educare_backend.child.application.ChildClassroomView;
import com.manuelaalecio.educare_backend.child.application.ChildGuardianCommand;
import com.manuelaalecio.educare_backend.child.application.ChildGuardianView;
import com.manuelaalecio.educare_backend.child.application.ChildService;
import com.manuelaalecio.educare_backend.child.application.ChildSummaryView;
import com.manuelaalecio.educare_backend.child.application.ChildView;
import com.manuelaalecio.educare_backend.child.domain.BirthCertificateAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.BirthDateInFutureException;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroom;
import com.manuelaalecio.educare_backend.child.domain.ChildNotFoundException;
import com.manuelaalecio.educare_backend.child.domain.ClassroomNotFoundForChildException;
import com.manuelaalecio.educare_backend.child.domain.CpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.GuardianNotFoundForChildException;
import com.manuelaalecio.educare_backend.child.domain.Relationship;
import com.manuelaalecio.educare_backend.child.domain.RgAlreadyInUseException;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianContact;
import com.manuelaalecio.educare_backend.guardian.application.GuardianInput;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianBirthDateInFutureException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ChildController.class)
@Import({ChildMapper.class, SecurityConfiguration.class, JwtConfiguration.class, ClockConfiguration.class,
		ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class, AccessTokens.class})
class ChildControllerTest {

	private static final String CHILDREN = "/api/v1/children";
	private static final UUID TURMA_1 = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final UUID TURMA_2 = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c2");
	private static final ClassroomRef TURMA_1_REF = new ClassroomRef(TURMA_1, "Turma 1");
	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID COORDINATOR_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000010");
	private static final UUID EDUCATOR_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000020");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String VALID_BODY = """
			{"name": "Maria Clara Souza", "birthDate": "2018-05-10", "cep": "01310-100",
			 "address": "Avenida Paulista", "neighborhood": "Bela Vista", "addressNumber": "1000",
			 "city": "São Paulo", "state": "SP", "classroomId": "0190f4a2-0000-7000-8000-0000000000c1",
			 "guardians": [{"guardianId": "0190f4a2-0000-7000-8000-0000000000d1", "relationship": "MOTHER"}]}""";
	private static final String MARIA_ID_TEXT = "0190f4a2-0000-7000-8000-0000000000d1";
	private static final UUID MARIA_ID = UUID.fromString(MARIA_ID_TEXT);
	private static final UUID CARLOS_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d3");
	private static final List<ChildGuardianCommand> GUARDIANS = List
		.of(ChildGuardianCommand.existing(MARIA_ID, Relationship.MOTHER));
	private static final List<ChildGuardianView> GUARDIAN_VIEWS = List.of(
			new ChildGuardianView(new GuardianContact(CARLOS_ID, "Carlos Souza", "11966665555", true, null, null),
					Relationship.GRANDFATHER),
			new ChildGuardianView(new GuardianContact(MARIA_ID, "Maria Souza", "11912345678", false,
					"Padaria Central", "1133334444"), Relationship.MOTHER));
	private static final String MARIA_JSON = """
			{"name": "Maria Souza", "phone": "11912345678", "notWorking": false, "workplace": "Padaria Central",
			 "workAddress": "Rua das Flores, 100", "workPhone": "1133334444"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccessTokens accessTokens;

	@MockitoBean
	private ChildService childService;

	@MockitoBean
	private AuthenticatedUserLookup lookup;

	@BeforeEach
	void setUpAuthenticatedUsers() {
		given(lookup.findById(COORDINATOR_ID))
			.willReturn(Optional.of(new TokenOwner(new AuthenticatedUser(COORDINATOR_ID, "COORDENADOR"), null)));
		given(lookup.findById(EDUCATOR_ID))
			.willReturn(Optional.of(new TokenOwner(new AuthenticatedUser(EDUCATOR_ID, "EDUCADOR"), null)));
	}

	// ---------- authentication and authorization ----------

	static Stream<Arguments> endpoints() {
		return Stream.of(
				Arguments.of("create", (Supplier<MockHttpServletRequestBuilder>) () -> postJson(VALID_BODY)),
				Arguments.of("list", (Supplier<MockHttpServletRequestBuilder>) () -> get(CHILDREN)),
				Arguments.of("findById", (Supplier<MockHttpServletRequestBuilder>) () -> get(CHILDREN + "/{id}", ID)),
				Arguments.of("update", (Supplier<MockHttpServletRequestBuilder>) () -> putJson(VALID_BODY)),
				Arguments.of("history", (Supplier<MockHttpServletRequestBuilder>) () ->
						get(CHILDREN + "/{id}/classrooms", ID)),
				Arguments.of("delete", (Supplier<MockHttpServletRequestBuilder>) () ->
						delete(CHILDREN + "/{id}", ID)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("endpoints")
	void shouldRespondUnauthorizedProblemWhenTokenIsMissing(String endpoint,
			Supplier<MockHttpServletRequestBuilder> request) throws Exception {
		mockMvc.perform(request.get())
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldLetEducatorCreateAndUpdateChild() throws Exception {
		// given
		given(childService.create(any(), any(), any())).willReturn(view(aChild()));
		given(childService.update(eq(ID), any(), any(), any())).willReturn(view(aChild()));

		// when / then
		performAs(EDUCATOR_ID, postJson(VALID_BODY)).andExpect(status().isCreated());
		performAs(EDUCATOR_ID, putJson(VALID_BODY)).andExpect(status().isOk());
	}

	@Test
	void shouldRespondForbiddenWhenEducatorDeletesChild() throws Exception {
		performAs(EDUCATOR_ID, delete(CHILDREN + "/{id}", ID))
			.andExpect(status().isForbidden())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(childService).shouldHaveNoInteractions();
	}

	// ---------- create ----------

	@Test
	void shouldCreateChildWithLocationAndFullBodyWhenRequestIsValid() throws Exception {
		// given
		given(childService.create(any(), any(), any())).willReturn(view(aCompleteChild()));

		// when
		ResultActions result = perform(postJson(VALID_BODY));

		// then
		result.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, CHILDREN + "/" + ID))
			.andExpect(jsonPath("$.id").value(ID.toString()))
			.andExpect(jsonPath("$.name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.birthDate").value("2018-05-10"))
			.andExpect(jsonPath("$.cep").value("01310100"))
			.andExpect(jsonPath("$.state").value("SP"))
			.andExpect(jsonPath("$.race").value("BROWN"))
			.andExpect(jsonPath("$.cpf").value("52998224725"))
			.andExpect(jsonPath("$.rg").value("123456789"))
			.andExpect(jsonPath("$.birthCertificateNumber").value(BIRTH_CERTIFICATE_NUMBER))
			.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"))
			.andExpect(jsonPath("$.createdBy").doesNotExist())
			.andExpect(jsonPath("$.updatedBy").doesNotExist());
	}

	@Test
	void shouldPassStrippedAndUpperCasedDataWhenCreating() throws Exception {
		// given
		given(childService.create(any(), any(), any())).willReturn(view(aChild()));

		// when
		perform(postJson(VALID_BODY.replace("\"SP\"", "\"sp\"").replace("\"Maria Clara Souza\"",
				"\"  Maria Clara Souza \"")))
			.andExpect(status().isCreated());

		// then
		then(childService).should()
			.create(aChild().cep("01310-100").build(), TURMA_1, GUARDIANS);
	}

	@Test
	void shouldListEveryRequiredFieldWhenBodyIsEmpty() throws Exception {
		perform(postJson("{}")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "birthDate", "cep", "address",
					"neighborhood", "addressNumber", "city", "state", "classroomId", "guardians")));
		then(childService).shouldHaveNoInteractions();
	}

	static Stream<Arguments> invalidFields() {
		return Stream.of(
				Arguments.of("name", "\"" + "a".repeat(151) + "\""),
				Arguments.of("medicalObservation", "\"" + "a".repeat(2001) + "\""),
				Arguments.of("cep", "\"01310-10\""),
				Arguments.of("cep", "\"0131A-100\""),
				Arguments.of("state", "\"XX\""),
				Arguments.of("state", "\"São Paulo\""),
				Arguments.of("race", "\"brown\""),
				Arguments.of("gender", "\"F\""),
				Arguments.of("maritalStatus", "\"DATING\""),
				Arguments.of("cpf", "\"529.982.247-24\""),
				Arguments.of("cpf", "\"11111111111\""),
				Arguments.of("rg", "\"...\""),
				Arguments.of("birthCertificateNumber", "\"1045390155201310001202100001232\""),
				Arguments.of("birthCertificateNumber", "\"1045390155201310001202100001232X\""),
				Arguments.of("birthDate", "\"10/05/2018\""),
				Arguments.of("birthDate", "\"2018-02-30\""));
	}

	@ParameterizedTest(name = "{0} = {1}")
	@MethodSource("invalidFields")
	void shouldRespondFieldErrorWhenFieldIsInvalid(String field, String value) throws Exception {
		perform(postJson(withField(field, value))).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value(field));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondFieldErrorWhenBirthDateIsInFuture() throws Exception {
		// given
		given(childService.create(any(), any(), any())).willThrow(new BirthDateInFutureException());

		// when / then
		perform(postJson(VALID_BODY)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("birthDate"));
	}

	@Test
	void shouldRespondBadRequestWithoutErrorsWhenJsonIsBroken() throws Exception {
		perform(postJson("{\"name\": \"Maria\"")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors").doesNotExist());
		then(childService).shouldHaveNoInteractions();
	}

	static Stream<Arguments> conflicts() {
		return Stream.of(
				Arguments.of(new CpfAlreadyInUseException(), "CPF já cadastrado"),
				Arguments.of(new RgAlreadyInUseException(), "RG já cadastrado"),
				Arguments.of(new BirthCertificateAlreadyInUseException(), "Matrícula da certidão já cadastrada"));
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("conflicts")
	void shouldRespondConflictWithoutDocumentWhenDocumentIsInUse(RuntimeException conflict, String detail)
			throws Exception {
		// given
		given(childService.create(any(), any(), any())).willThrow(conflict);

		// when / then
		perform(postJson(withField(withField("cpf", "\"529.982.247-25\""), "birthCertificateNumber",
				"\"" + BIRTH_CERTIFICATE_NUMBER + "\"")))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(detail))
			.andExpect(content().string(not(containsString("52998224725"))))
			.andExpect(content().string(not(containsString("529.982.247-25"))))
			.andExpect(content().string(not(containsString("10453901552"))));
	}

	@Test
	void shouldPassClassroomAndReturnItWhenCreating() throws Exception {
		// given
		given(childService.create(any(), eq(TURMA_1), eq(GUARDIANS))).willReturn(view(aChild()));

		// when / then
		perform(postJson(VALID_BODY)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.classroom.id").value(TURMA_1.toString()))
			.andExpect(jsonPath("$.classroom.name").value("Turma 1"));
	}

	@Test
	void shouldRespondFieldErrorWhenClassroomIsMissing() throws Exception {
		perform(postJson(VALID_BODY.replace(", \"classroomId\": \"" + TURMA_1 + "\"", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("classroomId"));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondFieldErrorWhenClassroomIdIsMalformedInBody() throws Exception {
		perform(postJson(withField("classroomId", "\"turma-1\""))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("classroomId"));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondFieldErrorWhenClassroomDoesNotExist() throws Exception {
		// given
		given(childService.create(any(), any(), any())).willThrow(new ClassroomNotFoundForChildException());

		// when / then
		perform(postJson(VALID_BODY)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("classroomId"))
			.andExpect(jsonPath("$.errors[0].message").value("Turma não encontrada"));
	}

	// ---------- guardians ----------

	@Test
	void shouldReturnGuardiansByNameWithContactOnlyWhenCreating() throws Exception {
		// given
		given(childService.create(any(), any(), any())).willReturn(view(aChild()));

		// when / then
		perform(postJson(VALID_BODY)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.guardians.length()").value(2))
			.andExpect(jsonPath("$.guardians[0].id").value(CARLOS_ID.toString()))
			.andExpect(jsonPath("$.guardians[0].name").value("Carlos Souza"))
			.andExpect(jsonPath("$.guardians[0].relationship").value("GRANDFATHER"))
			.andExpect(jsonPath("$.guardians[0].phone").value("11966665555"))
			.andExpect(jsonPath("$.guardians[0].notWorking").value(true))
			.andExpect(jsonPath("$.guardians[0].workplace").isEmpty())
			.andExpect(jsonPath("$.guardians[0].workPhone").isEmpty())
			.andExpect(jsonPath("$.guardians[0].length()").value(7))
			.andExpect(jsonPath("$.guardians[1].name").value("Maria Souza"))
			.andExpect(jsonPath("$.guardians[1].relationship").value("MOTHER"))
			.andExpect(jsonPath("$.guardians[1].workplace").value("Padaria Central"))
			.andExpect(jsonPath("$.guardians[1].workPhone").value("1133334444"));
	}

	@Test
	void shouldReturnGuardiansWhenUpdatingAndFindingChild() throws Exception {
		// given
		given(childService.update(eq(ID), any(), any(), any())).willReturn(view(aChild()));
		given(childService.findById(ID)).willReturn(new ChildView(stored(aChild()), TURMA_1_REF, List.of()));

		// when / then
		perform(putJson(VALID_BODY)).andExpect(status().isOk()).andExpect(jsonPath("$.guardians.length()").value(2));
		perform(get(CHILDREN + "/{id}", ID)).andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians").isArray())
			.andExpect(jsonPath("$.guardians").isEmpty());
	}

	@Test
	void shouldPassExistingAndNewGuardiansInOrderWhenUpdating() throws Exception {
		// given
		given(childService.update(eq(ID), any(), any(), any())).willReturn(view(aChild()));

		// when
		perform(putJson(withGuardians("[{\"guardian\": " + MARIA_JSON.replace("\"notWorking\": false",
				"\"notWorking\": true") + ", \"relationship\": \"AUNT\"}, {\"guardianId\": \"" + CARLOS_ID
				+ "\", \"relationship\": \"GRANDFATHER\"}]")))
			.andExpect(status().isOk());

		// then
		then(childService).should()
			.update(ID, aChild().cep("01310-100").build(), TURMA_1,
					List.of(ChildGuardianCommand.newGuardian(new GuardianInput("Maria Souza", null, null, null, null,
							"11912345678", true, null, null, null), Relationship.AUNT),
							ChildGuardianCommand.existing(CARLOS_ID, Relationship.GRANDFATHER)));
	}

	static Stream<Arguments> invalidGuardianLists() {
		String existing = "{\"guardianId\": \"" + MARIA_ID_TEXT + "\", \"relationship\": \"MOTHER\"}";
		String newOne = "{\"guardian\": " + MARIA_JSON + ", \"relationship\": \"OTHER\"}";
		return Stream.of(
				Arguments.of("ausente", null, "Informe pelo menos um responsável."),
				Arguments.of("null", "null", "Informe pelo menos um responsável."),
				Arguments.of("vazio", "[]", "Informe pelo menos um responsável."),
				Arguments.of("seis itens", "[" + String.join(", ", java.util.Collections.nCopies(6, newOne)) + "]",
						"Informe no máximo 5 responsáveis."),
				Arguments.of("id repetido", "[" + existing + ", " + existing.replace("MOTHER", "OTHER") + "]",
						"O mesmo responsável não pode ser informado mais de uma vez."));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidGuardianLists")
	void shouldRespondErrorOnGuardiansWhenListIsInvalid(String name, String guardiansJson, String message)
			throws Exception {
		String body = guardiansJson == null ? VALID_BODY.substring(0, VALID_BODY.indexOf(",\n \"guardians\""))
				+ "}" : withGuardians(guardiansJson);
		perform(postJson(body)).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians"))
			.andExpect(jsonPath("$.errors[0].message").value(message));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldListGuardiansTogetherWithOtherFieldsWhenBothAreInvalid() throws Exception {
		perform(postJson(withField(withGuardians("[]"), "cep", "\"123\""))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians", "cep")));
	}

	@Test
	void shouldPointToItemWhenItIsNullOrHasNoneOrBothSources() throws Exception {
		perform(postJson(withGuardians("[null, {\"relationship\": \"MOTHER\"}, {\"guardianId\": \"" + MARIA_ID
				+ "\", \"guardian\": " + MARIA_JSON + ", \"relationship\": \"FATHER\"}]")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians[0]", "guardians[1]",
					"guardians[2]")));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldPointToRelationshipWhenItIsMissingOrNotInList() throws Exception {
		perform(postJson(withGuardians("[{\"guardian\": " + MARIA_JSON + "}, {\"guardian\": "
				+ MARIA_JSON.replace("Maria Souza", "João Souza") + ", \"relationship\": \"COUSIN\"}]")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians[0].relationship",
					"guardians[1].relationship")));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldPointToGuardianIdWhenItIsMalformed() throws Exception {
		perform(postJson(withGuardians("[{\"guardianId\": \"maria\", \"relationship\": \"MOTHER\"}]")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians[0].guardianId"));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldPointToGuardianIdWhenGuardianDoesNotExist() throws Exception {
		// given
		given(childService.create(any(), any(), any()))
			.willThrow(new GuardianNotFoundForChildException("guardians[0].guardianId"));

		// when / then
		perform(postJson(VALID_BODY)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians[0].guardianId"))
			.andExpect(jsonPath("$.errors[0].message").value("Responsável não encontrado"));
	}

	@Test
	void shouldPointToNewGuardianFieldsWithItemPathWhenTheyAreInvalid() throws Exception {
		perform(postJson(withGuardians("[{\"guardianId\": \"" + MARIA_ID + "\", \"relationship\": \"FATHER\"}, "
				+ "{\"guardian\": {\"name\": \"\", \"phone\": \"111234\", \"notWorking\": true, "
				+ "\"cpf\": \"123.456.789-00\", \"birthDate\": \"10/05/1990\"}, \"relationship\": \"MOTHER\"}]")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians[1].guardian.birthDate"));
		perform(postJson(withGuardians("[{\"guardianId\": \"" + MARIA_ID + "\", \"relationship\": \"FATHER\"}, "
				+ "{\"guardian\": {\"name\": \"\", \"phone\": \"111234\", \"notWorking\": true, "
				+ "\"cpf\": \"123.456.789-00\"}, \"relationship\": \"MOTHER\"}, "
				+ "{\"guardian\": {\"name\": \"Ana\", \"phone\": \"11912345678\"}, \"relationship\": \"AUNT\"}]")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians[1].guardian.name",
					"guardians[1].guardian.phone", "guardians[1].guardian.cpf", "guardians[2].guardian.workplace",
					"guardians[2].guardian.workAddress", "guardians[2].guardian.workPhone")));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondFieldErrorOnItemWhenNewGuardianBirthDateIsInFuture() throws Exception {
		// given
		given(childService.create(any(), any(), any()))
			.willThrow(new GuardianBirthDateInFutureException().withFieldPrefix("guardians[0].guardian."));

		// when / then
		perform(postJson(VALID_BODY)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("guardians[0].guardian.birthDate"));
	}

	@Test
	void shouldRespondConflictOnItemWithoutCpfWhenNewGuardianCpfIsInUse() throws Exception {
		// given
		given(childService.create(any(), any(), any()))
			.willThrow(new GuardianCpfAlreadyInUseException().withFieldPrefix("guardians[1].guardian."));

		// when / then
		perform(postJson(withGuardians("[{\"guardian\": " + MARIA_JSON + ", \"relationship\": \"MOTHER\"}, "
				+ "{\"guardian\": " + MARIA_JSON.replace("}", ", \"cpf\": \"529.982.247-25\"}")
				+ ", \"relationship\": \"AUNT\"}]")))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Já existe um responsável com este CPF."))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians[1].guardian.cpf"))
			.andExpect(content().string(not(containsString("52998224725"))))
			.andExpect(content().string(not(containsString("529.982.247-25"))));
	}

	// ---------- list ----------

	@Test
	void shouldListSummariesWithDefaultPageWhenNoParameterIsGiven() throws Exception {
		// given
		Pageable expected = PageRequest.of(0, 20, Sort.by("name"));
		given(childService.list(null, null, expected))
			.willReturn(new PageImpl<>(List.of(new ChildSummaryView(stored(aCompleteChild()), TURMA_1_REF)), expected,
					1));

		// when
		ResultActions result = perform(get(CHILDREN));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(ID.toString()))
			.andExpect(jsonPath("$.content[0].name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.content[0].birthDate").value("2018-05-10"))
			.andExpect(jsonPath("$.content[0].createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.content[0].updatedAt").value("2026-03-02T09:00:00Z"))
			.andExpect(jsonPath("$.content[0].classroom.id").value(TURMA_1.toString()))
			.andExpect(jsonPath("$.content[0].classroom.name").value("Turma 1"))
			.andExpect(jsonPath("$.content[0].classroom.length()").value(2))
			.andExpect(jsonPath("$.content[0].length()").value(6))
			.andExpect(jsonPath("$.content[0].guardians").doesNotExist())
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(jsonPath("$.page.number").value(0))
			.andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.page.totalPages").value(1));
	}

	@Test
	void shouldPassNameAndSortWhenGiven() throws Exception {
		// given
		Pageable expected = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "birthDate"));
		given(childService.list("joao", TURMA_1, expected)).willReturn(new PageImpl<>(List.of(), expected, 0));

		// when / then
		perform(get(CHILDREN).param("name", "joao").param("classroomId", TURMA_1.toString()).param("page", "1")
			.param("size", "5")
			.param("sort", "birthDate,desc")).andExpect(status().isOk());
		then(childService).should().list("joao", TURMA_1, expected);
	}

	@Test
	void shouldCapPageSizeWhenItIsAboveMaximum() throws Exception {
		// given
		given(childService.list(eq(null), eq(null), any())).willAnswer(invocation ->
				new PageImpl<>(List.of(), invocation.getArgument(2, Pageable.class), 0));

		// when / then
		perform(get(CHILDREN).param("size", "500")).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.size").value(100));
	}

	@ParameterizedTest
	@ValueSource(strings = {"cpf", "healthReport", "createdBy"})
	void shouldRespondBadRequestWhenSortPropertyIsNotAllowed(String property) throws Exception {
		perform(get(CHILDREN).param("sort", property)).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondBadRequestWhenClassroomFilterIsMalformed() throws Exception {
		perform(get(CHILDREN).param("classroomId", "turma-1")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(childService).shouldHaveNoInteractions();
	}

	// ---------- history ----------

	@Test
	void shouldReturnHistoryMostRecentFirstWhenChildExists() throws Exception {
		// given
		ChildClassroom old = ChildClassroom.open(ID, TURMA_1, LocalDate.of(2026, 10, 1));
		old.close(LocalDate.of(2026, 11, 1));
		ChildClassroom current = ChildClassroom.open(ID, TURMA_2, LocalDate.of(2026, 11, 1));
		given(childService.history(ID)).willReturn(List.of(
				new ChildClassroomView(current, new ClassroomRef(TURMA_2, "Turma 2")),
				new ChildClassroomView(old, TURMA_1_REF)));

		// when / then
		performAs(EDUCATOR_ID, get(CHILDREN + "/{id}/classrooms", ID)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].classroomId").value(TURMA_2.toString()))
			.andExpect(jsonPath("$[0].classroomName").value("Turma 2"))
			.andExpect(jsonPath("$[0].startDate").value("2026-11-01"))
			.andExpect(jsonPath("$[0].endDate").isEmpty())
			.andExpect(jsonPath("$[1].classroomName").value("Turma 1"))
			.andExpect(jsonPath("$[1].startDate").value("2026-10-01"))
			.andExpect(jsonPath("$[1].endDate").value("2026-11-01"));
	}

	@Test
	void shouldRespondNotFoundWhenReadingHistoryOfMissingChild() throws Exception {
		// given
		given(childService.history(ID)).willThrow(new ChildNotFoundException());

		// when / then
		perform(get(CHILDREN + "/{id}/classrooms", ID)).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	void shouldRespondBadRequestWhenReadingHistoryWithMalformedId() throws Exception {
		perform(get(CHILDREN + "/abc/classrooms")).andExpect(status().isBadRequest());
		then(childService).shouldHaveNoInteractions();
	}

	// ---------- findById ----------

	@Test
	void shouldReturnFullChildWhenItExists() throws Exception {
		// given
		given(childService.findById(ID)).willReturn(view(aChild()));

		// when / then
		perform(get(CHILDREN + "/{id}", ID)).andExpect(status().isOk())
			.andExpect(jsonPath("$.classroom.id").value(TURMA_1.toString()))
			.andExpect(jsonPath("$.classroom.name").value("Turma 1"))
			.andExpect(jsonPath("$.id").value(ID.toString()))
			.andExpect(jsonPath("$.cpf").isEmpty())
			.andExpect(jsonPath("$.birthCertificateNumber").isEmpty())
			.andExpect(jsonPath("$.createdBy").doesNotExist());
	}

	@Test
	void shouldRespondNotFoundWhenChildDoesNotExist() throws Exception {
		// given
		given(childService.findById(ID)).willThrow(new ChildNotFoundException());

		// when / then
		perform(get(CHILDREN + "/{id}", ID)).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Criança não encontrada"));
	}

	@Test
	void shouldRespondBadRequestWhenIdIsMalformed() throws Exception {
		perform(get(CHILDREN + "/abc")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(childService).shouldHaveNoInteractions();
	}

	// ---------- update ----------

	@Test
	void shouldReturnUpdatedChildWhenUpdateIsValid() throws Exception {
		// given
		given(childService.update(eq(ID), any(), any(), any())).willReturn(view(aChild().neighborhood("Consolação")));

		// when / then
		perform(putJson(VALID_BODY)).andExpect(status().isOk())
			.andExpect(jsonPath("$.neighborhood").value("Consolação"));
	}

	@Test
	void shouldRespondFieldErrorsWithoutUpdatingWhenUpdateIsInvalid() throws Exception {
		perform(putJson(withField("cep", "\"123\"").replace("\"Maria Clara Souza\"", "\"\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "cep")));
		then(childService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondNotFoundWhenUpdatingMissingChild() throws Exception {
		// given
		given(childService.update(eq(ID), any(), any(), any())).willThrow(new ChildNotFoundException());

		// when / then
		perform(putJson(VALID_BODY)).andExpect(status().isNotFound());
	}

	@Test
	void shouldRespondConflictWhenUpdateUsesCpfOfAnotherChild() throws Exception {
		// given
		given(childService.update(eq(ID), any(), any(), any())).willThrow(new CpfAlreadyInUseException());

		// when / then
		perform(putJson(withField("cpf", "\"52998224725\""))).andExpect(status().isConflict())
			.andExpect(content().string(not(containsString("52998224725"))));
	}

	// ---------- delete ----------

	@Test
	void shouldRespondNoContentWhenCoordinatorDeletesChild() throws Exception {
		perform(delete(CHILDREN + "/{id}", ID)).andExpect(status().isNoContent())
			.andExpect(content().string(""));
		then(childService).should().delete(ID);
	}

	@Test
	void shouldRespondNotFoundWhenDeletingMissingChild() throws Exception {
		// given
		willThrow(new ChildNotFoundException()).given(childService).delete(ID);

		// when / then
		perform(delete(CHILDREN + "/{id}", ID)).andExpect(status().isNotFound());
	}

	@Test
	void shouldRespondBadRequestWhenDeletingWithMalformedId() throws Exception {
		perform(delete(CHILDREN + "/abc")).andExpect(status().isBadRequest());
		then(childService).shouldHaveNoInteractions();
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return performAs(COORDINATOR_ID, request);
	}

	private ResultActions performAs(UUID userId, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(userId)));
	}

	/**
	 * The valid body with one more field (or the replacement of an existing one, since the later key wins).
	 */
	private static String withField(String field, String jsonValue) {
		return withField(VALID_BODY, field, jsonValue);
	}

	private static String withField(String body, String field, String jsonValue) {
		return body.substring(0, body.lastIndexOf('}')) + ", \"" + field + "\": " + jsonValue + "}";
	}

	private static String withGuardians(String guardiansJson) {
		return withField("guardians", guardiansJson);
	}

	private static ChildView view(ChildDetailsBuilder details) {
		return new ChildView(stored(details), TURMA_1_REF, GUARDIAN_VIEWS);
	}

	private static Child stored(ChildDetailsBuilder details) {
		Child child = new Child(details.build());
		ReflectionTestUtils.setField(child, "id", ID);
		ReflectionTestUtils.setField(child, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(child, "updatedAt", UPDATED_AT);
		return child;
	}

	private static MockHttpServletRequestBuilder postJson(String body) {
		return post(CHILDREN).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockHttpServletRequestBuilder putJson(String body) {
		return put(CHILDREN + "/" + ID).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
