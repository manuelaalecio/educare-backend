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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.child.ChildDetailsBuilder;
import com.manuelaalecio.educare_backend.child.application.ChildService;
import com.manuelaalecio.educare_backend.child.domain.BirthCertificateAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.BirthDateInFutureException;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildNotFoundException;
import com.manuelaalecio.educare_backend.child.domain.CpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.RgAlreadyInUseException;
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
	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID COORDINATOR_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000010");
	private static final UUID EDUCATOR_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000020");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String VALID_BODY = """
			{"name": "Maria Clara Souza", "birthDate": "2018-05-10", "cep": "01310-100",
			 "address": "Avenida Paulista", "neighborhood": "Bela Vista", "addressNumber": "1000",
			 "city": "São Paulo", "state": "SP"}""";

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
		given(childService.create(any())).willReturn(stored(aChild()));
		given(childService.update(eq(ID), any())).willReturn(stored(aChild()));

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
		given(childService.create(any())).willReturn(stored(aCompleteChild()));

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
		given(childService.create(any())).willReturn(stored(aChild()));

		// when
		perform(postJson(VALID_BODY.replace("\"SP\"", "\"sp\"").replace("\"Maria Clara Souza\"",
				"\"  Maria Clara Souza \"")))
			.andExpect(status().isCreated());

		// then
		then(childService).should()
			.create(aChild().cep("01310-100").build());
	}

	@Test
	void shouldListEveryRequiredFieldWhenBodyIsEmpty() throws Exception {
		perform(postJson("{}")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "birthDate", "cep", "address",
					"neighborhood", "addressNumber", "city", "state")));
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
		given(childService.create(any())).willThrow(new BirthDateInFutureException());

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
		given(childService.create(any())).willThrow(conflict);

		// when / then
		perform(postJson(withField("cpf", "\"529.982.247-25\"")
			.replace("}", ", \"birthCertificateNumber\": \"" + BIRTH_CERTIFICATE_NUMBER + "\"}")))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(detail))
			.andExpect(content().string(not(containsString("52998224725"))))
			.andExpect(content().string(not(containsString("529.982.247-25"))))
			.andExpect(content().string(not(containsString("10453901552"))));
	}

	// ---------- list ----------

	@Test
	void shouldListSummariesWithDefaultPageWhenNoParameterIsGiven() throws Exception {
		// given
		Pageable expected = PageRequest.of(0, 20, Sort.by("name"));
		given(childService.list(null, expected))
			.willReturn(new PageImpl<>(List.of(stored(aCompleteChild())), expected, 1));

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
			.andExpect(jsonPath("$.content[0].length()").value(5))
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(jsonPath("$.page.number").value(0))
			.andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.page.totalPages").value(1));
	}

	@Test
	void shouldPassNameAndSortWhenGiven() throws Exception {
		// given
		Pageable expected = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "birthDate"));
		given(childService.list("joao", expected)).willReturn(new PageImpl<>(List.of(), expected, 0));

		// when / then
		perform(get(CHILDREN).param("name", "joao").param("page", "1").param("size", "5")
			.param("sort", "birthDate,desc")).andExpect(status().isOk());
		then(childService).should().list("joao", expected);
	}

	@Test
	void shouldCapPageSizeWhenItIsAboveMaximum() throws Exception {
		// given
		given(childService.list(eq(null), any())).willAnswer(invocation ->
				new PageImpl<>(List.of(), invocation.getArgument(1, Pageable.class), 0));

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

	// ---------- findById ----------

	@Test
	void shouldReturnFullChildWhenItExists() throws Exception {
		// given
		given(childService.findById(ID)).willReturn(stored(aChild()));

		// when / then
		perform(get(CHILDREN + "/{id}", ID)).andExpect(status().isOk())
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
		given(childService.update(eq(ID), any())).willReturn(stored(aChild().neighborhood("Consolação")));

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
		given(childService.update(eq(ID), any())).willThrow(new ChildNotFoundException());

		// when / then
		perform(putJson(VALID_BODY)).andExpect(status().isNotFound());
	}

	@Test
	void shouldRespondConflictWhenUpdateUsesCpfOfAnotherChild() throws Exception {
		// given
		given(childService.update(eq(ID), any())).willThrow(new CpfAlreadyInUseException());

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
		return VALID_BODY.substring(0, VALID_BODY.lastIndexOf('}')) + ", \"" + field + "\": " + jsonValue + "}";
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
