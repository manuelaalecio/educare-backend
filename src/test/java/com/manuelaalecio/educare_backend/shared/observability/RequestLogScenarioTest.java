package com.manuelaalecio.educare_backend.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.TestUsers;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Scenarios of the requirement "Log por requisição sem dados pessoais" of the {@code observability} spec. The
 * scenario of a request refused by the rate limit is in the {@code RequestRateLimitScenarioTest}. The scenario with
 * the log turned off changes the level through the {@link LoggingSystem}, the same mechanism that applies
 * {@code logging.level.*}, and puts it back: the logging system is global to the JVM, so a context of its own with
 * the property would leave the level at {@code WARN} for the contexts already cached.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class})
@ExtendWith(OutputCaptureExtension.class)
class RequestLogScenarioTest {

	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String PASSWORD = "segredo123";
	private static final String CHILDREN = "/api/v1/children";
	private static final String DURATION = "durationMs=\\d+";
	private static final String REQUEST_LOG_LOGGER = "com.manuelaalecio.educare_backend.shared.observability";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private LoggingSystem loggingSystem;

	private UUID anaId;
	private String anaBearer;
	private UUID classroomId;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("DELETE FROM child_classrooms");
		jdbcTemplate.update("DELETE FROM children");
		jdbcTemplate.update("DELETE FROM classrooms");
		jdbcTemplate.update("DELETE FROM users");
		anaId = testUsers.create("Ana Souza", ANA_LOGIN, PASSWORD, "COORDENADOR");
		anaBearer = testUsers.bearer(ANA_LOGIN, PASSWORD);
		String classroom = mockMvc.perform(authenticated(post("/api/v1/classrooms"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Turma 1\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		classroomId = UUID.fromString(JsonPath.read(classroom, "$.id"));
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Consulta registrada com a rota como modelo")
	void shouldLogRoutePatternWhenChildIsReadById(CapturedOutput output) throws Exception {
		// given
		String id = createChild("Maria Clara Souza", null);
		int start = output.length();

		// when
		mockMvc.perform(authenticated(get(CHILDREN + "/" + id))).andExpect(status().isOk());

		// then
		String log = output.toString().substring(start);
		assertThat(requestLines(log)).singleElement().asString()
			.matches("http method=GET route=/api/v1/children/\\{id} status=200 " + DURATION);
		assertThat(log).doesNotContain(id);
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Query string fora do log")
	void shouldLeaveQueryStringOutWhenChildrenAreSearched(CapturedOutput output) throws Exception {
		// given
		int start = output.length();

		// when
		mockMvc.perform(authenticated(get(CHILDREN + "?name=Maria&page=0"))).andExpect(status().isOk());

		// then
		String log = output.toString().substring(start);
		assertThat(requestLines(log)).singleElement().asString()
			.matches("http method=GET route=/api/v1/children status=200 " + DURATION);
		assertThat(log).doesNotContain("Maria");
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Corpo e usuário fora do log")
	void shouldLeaveBodyAndUserOutWhenChildIsCreated(CapturedOutput output) throws Exception {
		// given
		int start = output.length();

		// when
		createChild("Maria Clara Silva", "529.982.247-25");

		// then
		String log = output.toString().substring(start);
		assertThat(requestLines(log)).singleElement().asString()
			.matches("http method=POST route=/api/v1/children status=201 " + DURATION);
		assertThat(log).doesNotContain("Maria Clara Silva", "52998224725", "529.982.247-25", ANA_LOGIN,
				anaId.toString(), anaBearer.substring("Bearer ".length()));
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Erro registrado com o status")
	void shouldLogStatusWhenChildDoesNotExist(CapturedOutput output) throws Exception {
		// given
		int start = output.length();

		// when
		mockMvc.perform(authenticated(get(CHILDREN + "/00000000-0000-0000-0000-000000000000")))
			.andExpect(status().isNotFound());

		// then
		assertThat(requestLines(output.toString().substring(start))).singleElement().asString()
			.matches("http method=GET route=/api/v1/children/\\{id} status=404 " + DURATION);
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Requisição recusada pela segurança")
	void shouldLogUnmatchedWhenSecurityRefusesRequest(CapturedOutput output) throws Exception {
		// given
		int start = output.length();

		// when
		mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());

		// then
		assertThat(requestLines(output.toString().substring(start))).singleElement().asString()
			.matches("http method=GET route=unmatched status=401 " + DURATION);
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Rota inexistente")
	void shouldLogUnmatchedWhenRouteDoesNotExist(CapturedOutput output) throws Exception {
		// given
		int start = output.length();

		// when
		mockMvc.perform(authenticated(get("/api/v1/nao-existe/123"))).andExpect(status().isNotFound());

		// then
		String log = output.toString().substring(start);
		assertThat(requestLines(log)).singleElement().asString()
			.matches("http method=GET route=unmatched status=404 " + DURATION);
		assertThat(log).doesNotContain("/api/v1/nao-existe/123");
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Health fora do log")
	void shouldNotLogWhenHealthIsRequested(CapturedOutput output) throws Exception {
		// given
		int start = output.length();

		// when
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

		// then
		assertThat(requestLines(output.toString().substring(start))).isEmpty();
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Log desligado pela configuração")
	void shouldNotLogWhenRequestLogLevelIsWarn(CapturedOutput output) throws Exception {
		// given
		LoggerConfiguration original = loggingSystem.getLoggerConfiguration(REQUEST_LOG_LOGGER);
		loggingSystem.setLogLevel(REQUEST_LOG_LOGGER, LogLevel.WARN);
		try {
			int start = output.length();

			// when
			mockMvc.perform(authenticated(get("/api/v1/auth/me"))).andExpect(status().isOk());

			// then
			assertThat(requestLines(output.toString().substring(start))).isEmpty();
		}
		finally {
			loggingSystem.setLogLevel(REQUEST_LOG_LOGGER,
					original == null ? null : original.getConfiguredLevel());
		}
	}

	private String createChild(String name, String cpf) throws Exception {
		String cpfField = cpf == null ? "" : ", \"cpf\": \"" + cpf + "\"";
		String body = """
				{"name": "%s", "birthDate": "2018-05-10", "cep": "01310-100", "address": "Avenida Paulista",
				"neighborhood": "Bela Vista", "addressNumber": "1000", "city": "São Paulo", "state": "SP",
				"classroomId": "%s"%s}
				""".formatted(name, classroomId, cpfField);
		String response = mockMvc.perform(authenticated(post(CHILDREN)).contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, anaBearer);
	}

	/** The request log lines in the output, from {@code http method=} to the end of the line. */
	private static List<String> requestLines(String output) {
		return output.lines()
			.filter(line -> line.contains("http method="))
			.map(line -> line.substring(line.indexOf("http method=")).strip())
			.toList();
	}

}
