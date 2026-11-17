package com.manuelaalecio.educare_backend.guardian.api;

import static com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder.aCompleteGuardian;
import static com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder.aGuardian;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder;
import com.manuelaalecio.educare_backend.guardian.application.GuardianChildRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianInput;
import com.manuelaalecio.educare_backend.guardian.application.GuardianService;
import com.manuelaalecio.educare_backend.guardian.application.GuardianSummaryView;
import com.manuelaalecio.educare_backend.guardian.application.GuardianView;
import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianBirthDateInFutureException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianNotFoundException;
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

@WebMvcTest(GuardianController.class)
@Import({GuardianMapper.class, SecurityConfiguration.class, JwtConfiguration.class, ClockConfiguration.class,
		ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class, AccessTokens.class})
class GuardianControllerTest {

	private static final String GUARDIANS = "/api/v1/guardians";
	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final UUID PEDRO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c2");
	private static final UUID COORDINATOR_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000010");
	private static final UUID EDUCATOR_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000020");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final List<GuardianChildRef> CHILDREN = List.of(new GuardianChildRef(ANA_ID, "Ana Souza", "MOTHER"),
			new GuardianChildRef(PEDRO_ID, "Pedro Souza", "STEPMOTHER"));
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String VALID_BODY = """
			{"name": "Maria Souza", "phone": "11912345678", "notWorking": false, "workplace": "Padaria Central",
			 "workAddress": "Rua das Flores, 100", "workPhone": "1133334444"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccessTokens accessTokens;

	@MockitoBean
	private GuardianService guardianService;

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
		return Stream.of(Arguments.of("list", (Supplier<MockHttpServletRequestBuilder>) () -> get(GUARDIANS)),
				Arguments.of("findById", (Supplier<MockHttpServletRequestBuilder>) () -> get(GUARDIANS + "/{id}", ID)),
				Arguments.of("update", (Supplier<MockHttpServletRequestBuilder>) () -> putJson(VALID_BODY)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("endpoints")
	void shouldRespondUnauthorizedProblemWhenTokenIsMissing(String endpoint,
			Supplier<MockHttpServletRequestBuilder> request) throws Exception {
		mockMvc.perform(request.get())
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(guardianService).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@ValueSource(strings = {"0190f4a2-0000-7000-8000-000000000010", "0190f4a2-0000-7000-8000-000000000020"})
	void shouldLetCoordinatorAndEducatorListReadAndUpdate(UUID userId) throws Exception {
		// given
		given(guardianService.list(any(), any())).willAnswer(invocation ->
				new PageImpl<>(List.of(), invocation.getArgument(1, Pageable.class), 0));
		given(guardianService.findById(ID)).willReturn(view(aGuardian()));
		given(guardianService.update(eq(ID), any())).willReturn(view(aGuardian()));

		// when / then
		performAs(userId, get(GUARDIANS)).andExpect(status().isOk());
		performAs(userId, get(GUARDIANS + "/{id}", ID)).andExpect(status().isOk());
		performAs(userId, putJson(VALID_BODY)).andExpect(status().isOk());
	}

	// ---------- list ----------

	@Test
	void shouldListOnlySummaryAndChildrenWithDefaultPageWhenNoParameterIsGiven() throws Exception {
		// given
		Pageable expected = PageRequest.of(0, 20, Sort.by("name"));
		given(guardianService.list(null, expected)).willReturn(new PageImpl<>(
				List.of(new GuardianSummaryView(stored(aCompleteGuardian()), CHILDREN)), expected, 1));

		// when
		ResultActions result = perform(get(GUARDIANS));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].length()").value(4))
			.andExpect(jsonPath("$.content[0].id").value(ID.toString()))
			.andExpect(jsonPath("$.content[0].name").value("Maria Souza"))
			.andExpect(jsonPath("$.content[0].phone").value("11912345678"))
			.andExpect(jsonPath("$.content[0].children.length()").value(2))
			.andExpect(jsonPath("$.content[0].children[0].id").value(ANA_ID.toString()))
			.andExpect(jsonPath("$.content[0].children[0].name").value("Ana Souza"))
			.andExpect(jsonPath("$.content[0].children[0].relationship").value("MOTHER"))
			.andExpect(jsonPath("$.content[0].children[0].length()").value(3))
			.andExpect(jsonPath("$.content[0].children[1].relationship").value("STEPMOTHER"))
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(jsonPath("$.page.number").value(0))
			.andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.page.totalPages").value(1))
			.andExpect(content().string(not(containsString("52998224725"))));
	}

	@Test
	void shouldPassSearchAndSortWhenGiven() throws Exception {
		// given
		Pageable expected = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
		given(guardianService.list("joao", expected)).willReturn(new PageImpl<>(List.of(), expected, 0));

		// when / then
		perform(get(GUARDIANS).param("q", "joao").param("page", "1").param("size", "5").param("sort",
				"createdAt,desc")).andExpect(status().isOk());
		then(guardianService).should().list("joao", expected);
	}

	@Test
	void shouldCapPageSizeWhenItIsAboveMaximum() throws Exception {
		// given
		given(guardianService.list(eq(null), any())).willAnswer(invocation ->
				new PageImpl<>(List.of(), invocation.getArgument(1, Pageable.class), 0));

		// when / then
		perform(get(GUARDIANS).param("size", "500")).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.size").value(100));
	}

	@ParameterizedTest
	@ValueSource(strings = {"cpf", "phone", "createdBy"})
	void shouldRespondBadRequestWhenSortPropertyIsNotAllowed(String property) throws Exception {
		perform(get(GUARDIANS).param("sort", property)).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(guardianService).shouldHaveNoInteractions();
	}

	// ---------- findById ----------

	@Test
	void shouldReturnFullGuardianWithChildrenWhenItExists() throws Exception {
		// given
		given(guardianService.findById(ID)).willReturn(new GuardianView(stored(aCompleteGuardian()), CHILDREN));

		// when / then
		perform(get(GUARDIANS + "/{id}", ID)).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ID.toString()))
			.andExpect(jsonPath("$.name").value("Maria Souza"))
			.andExpect(jsonPath("$.maritalStatus").value("MARRIED"))
			.andExpect(jsonPath("$.birthDate").value("1990-05-10"))
			.andExpect(jsonPath("$.cpf").value("52998224725"))
			.andExpect(jsonPath("$.rg").value("12345678X"))
			.andExpect(jsonPath("$.phone").value("11912345678"))
			.andExpect(jsonPath("$.notWorking").value(false))
			.andExpect(jsonPath("$.workplace").value("Padaria Central"))
			.andExpect(jsonPath("$.workAddress").value("Rua das Flores, 100"))
			.andExpect(jsonPath("$.workPhone").value("1133334444"))
			.andExpect(jsonPath("$.children[*].name").value(contains("Ana Souza", "Pedro Souza")))
			.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"))
			.andExpect(jsonPath("$.createdBy").doesNotExist())
			.andExpect(jsonPath("$.updatedBy").doesNotExist());
	}

	@Test
	void shouldReturnNullOptionalsWhenGuardianHasOnlyRequiredData() throws Exception {
		// given
		given(guardianService.findById(ID)).willReturn(view(aGuardian().notWorking(true)));

		// when / then
		perform(get(GUARDIANS + "/{id}", ID)).andExpect(status().isOk())
			.andExpect(jsonPath("$.maritalStatus").isEmpty())
			.andExpect(jsonPath("$.birthDate").isEmpty())
			.andExpect(jsonPath("$.cpf").isEmpty())
			.andExpect(jsonPath("$.rg").isEmpty())
			.andExpect(jsonPath("$.workplace").isEmpty())
			.andExpect(jsonPath("$.workAddress").isEmpty())
			.andExpect(jsonPath("$.workPhone").isEmpty())
			.andExpect(jsonPath("$.children").isEmpty());
	}

	@Test
	void shouldRespondNotFoundWhenGuardianDoesNotExist() throws Exception {
		// given
		given(guardianService.findById(ID)).willThrow(new GuardianNotFoundException());

		// when / then
		perform(get(GUARDIANS + "/{id}", ID)).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Responsável não encontrado"));
	}

	@Test
	void shouldRespondBadRequestWhenIdIsMalformed() throws Exception {
		perform(get(GUARDIANS + "/abc")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		perform(put(GUARDIANS + "/abc").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		then(guardianService).shouldHaveNoInteractions();
	}

	// ---------- update ----------

	@Test
	void shouldPassStrippedDataAndReturnUpdatedGuardianWhenUpdateIsValid() throws Exception {
		// given
		given(guardianService.update(eq(ID), any())).willReturn(view(aGuardian().name("Maria Souza Lima")));

		// when
		perform(putJson(VALID_BODY.replace("\"Maria Souza\"", "\"  Maria Souza Lima \"")
			.replace("}", ", \"rg\": \"   \", \"maritalStatus\": \"DIVORCED\"}")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Maria Souza Lima"));

		// then
		then(guardianService).should()
			.update(ID, new GuardianInput("Maria Souza Lima", "DIVORCED", null, null, null, "11912345678", false,
					"Padaria Central", "Rua das Flores, 100", "1133334444"));
	}

	@Test
	void shouldDiscardWorkFieldsWhenNotWorking() throws Exception {
		// given
		given(guardianService.update(eq(ID), any())).willReturn(view(aGuardian().notWorking(true)));

		// when
		perform(putJson("""
				{"name": "Maria Souza", "phone": "11912345678", "notWorking": true, "workplace": "Padaria Central"}"""))
			.andExpect(status().isOk());

		// then
		then(guardianService).should()
			.update(ID, new GuardianInput("Maria Souza", null, null, null, null, "11912345678", true, null, null,
					null));
	}

	@Test
	void shouldListEveryRequiredFieldWhenBodyIsEmpty() throws Exception {
		perform(putJson("{}")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "phone", "workplace",
					"workAddress", "workPhone")));
		then(guardianService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRequireTheThreeWorkFieldsWhenWorking() throws Exception {
		perform(putJson("""
				{"name": "Maria Souza", "phone": "11912345678", "notWorking": false, "workAddress": "   "}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("workplace", "workAddress",
					"workPhone")));
		then(guardianService).shouldHaveNoInteractions();
	}

	static Stream<Arguments> invalidFields() {
		return Stream.of(
				Arguments.of("name", "\"" + "a".repeat(151) + "\""),
				Arguments.of("name", "\"   \""),
				Arguments.of("workplace", "\"" + "a".repeat(151) + "\""),
				Arguments.of("workAddress", "\"" + "a".repeat(201) + "\""),
				Arguments.of("rg", "\"" + "1".repeat(21) + "\""),
				Arguments.of("rg", "\"...\""),
				Arguments.of("phone", "\"111234\""),
				Arguments.of("phone", "\"(11) 91234-5678\""),
				Arguments.of("workPhone", "\"(11) 3333-4444\""),
				Arguments.of("workPhone", "\"113333444455\""),
				Arguments.of("cpf", "\"123.456.789-00\""),
				Arguments.of("cpf", "\"11111111111\""),
				Arguments.of("maritalStatus", "\"married\""),
				Arguments.of("birthDate", "\"10/05/1990\""),
				Arguments.of("notWorking", "\"talvez\""));
	}

	@ParameterizedTest(name = "{0} = {1}")
	@MethodSource("invalidFields")
	void shouldRespondFieldErrorWhenFieldIsInvalid(String field, String value) throws Exception {
		perform(putJson(withField(field, value))).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value(field));
		then(guardianService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondFieldErrorWhenBirthDateIsInFuture() throws Exception {
		// given
		given(guardianService.update(eq(ID), any())).willThrow(new GuardianBirthDateInFutureException());

		// when / then
		perform(putJson(withField("birthDate", "\"2026-03-02\""))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("birthDate"));
	}

	@Test
	void shouldRespondBadRequestWithoutErrorsWhenJsonIsBroken() throws Exception {
		perform(putJson("{\"name\": \"Maria\"")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors").doesNotExist());
		then(guardianService).shouldHaveNoInteractions();
	}

	@Test
	void shouldRespondNotFoundWhenUpdatingMissingGuardian() throws Exception {
		// given
		given(guardianService.update(eq(ID), any())).willThrow(new GuardianNotFoundException());

		// when / then
		perform(putJson(VALID_BODY)).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	void shouldRespondConflictWithCpfFieldWithoutNumberWhenCpfIsInUse() throws Exception {
		// given
		given(guardianService.update(eq(ID), any())).willThrow(new GuardianCpfAlreadyInUseException());

		// when / then
		perform(putJson(withField("cpf", "\"529.982.247-25\""))).andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Já existe um responsável com este CPF."))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("cpf"))
			.andExpect(jsonPath("$.errors[0].message").value("Já existe um responsável com este CPF."))
			.andExpect(content().string(not(containsString("52998224725"))))
			.andExpect(content().string(not(containsString("529.982.247-25"))));
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
		return VALID_BODY.substring(0, VALID_BODY.lastIndexOf('}')) + ", \"" + field + "\": " + jsonValue + "}";
	}

	private static GuardianView view(GuardianDetailsBuilder details) {
		return new GuardianView(stored(details), List.of());
	}

	private static Guardian stored(GuardianDetailsBuilder details) {
		Guardian guardian = new Guardian(details.build());
		ReflectionTestUtils.setField(guardian, "id", ID);
		ReflectionTestUtils.setField(guardian, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(guardian, "updatedAt", UPDATED_AT);
		return guardian;
	}

	private static MockHttpServletRequestBuilder putJson(String body) {
		return put(GUARDIANS + "/" + ID).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
