package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.function.Supplier;

import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.TestUsers;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Scenarios of the requirement "Limite de requisições por usuário autenticado" of the {@code security} spec, and the
 * scenario "Requisição recusada pelo limite" of the {@code observability} spec, with the production limit (the test
 * classpath raises it for the rest of the suite). The counts live in the limiter of the context, by user id; every
 * test creates its users again, with new ids, so no count crosses tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "educare.rate-limit.max-requests=120")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class})
@ExtendWith(OutputCaptureExtension.class)
class RequestRateLimitScenarioTest {

	private static final String REQUIREMENT = "Limite de requisições por usuário autenticado — Scenario: ";
	private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
	private static final int LIMIT = 120;
	private static final String ME = "/api/v1/auth/me";
	private static final String CLASSROOMS = "/api/v1/classrooms";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String BRUNO_LOGIN = "bruno.lima@educare.org";
	private static final String ANA_PASSWORD = "segredo123";
	private static final String BRUNO_PASSWORD = "senhaBruno1";
	private static final String DETAIL = "Muitas requisições. Aguarde um instante.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@Autowired
	private TestUsers testUsers;

	private String ana;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("DELETE FROM child_classrooms");
		jdbcTemplate.update("DELETE FROM children");
		jdbcTemplate.update("DELETE FROM classrooms");
		jdbcTemplate.update("DELETE FROM users");
		clock.setInstant(T0);
		testUsers.create("Ana Souza", ANA_LOGIN, ANA_PASSWORD, "COORDENADOR");
		ana = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);
	}

	@Test
	@DisplayName(REQUIREMENT + "Requisições dentro do limite")
	void shouldAcceptRequestsWhenWithinLimit() throws Exception {
		// when / then
		performTimes(LIMIT, ana, () -> get(ME), status().isOk());
	}

	@Test
	@DisplayName(REQUIREMENT + "Requisição acima do limite")
	void shouldRefuseWithProblemAndRetryAfterWhenLimitIsExceeded() throws Exception {
		// given
		performTimes(LIMIT, ana, () -> get(ME), status().isOk());
		clock.setInstant(T0.plusSeconds(40));

		// when / then
		perform(ana, get(ME))
			.andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.detail").value(DETAIL))
			.andExpect(jsonPath("$.errors").doesNotExist())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "20"));
	}

	@Test
	@DisplayName(REQUIREMENT + "Requisição recusada não executa a operação")
	void shouldNotRunOperationWhenRequestIsRefused() throws Exception {
		// given
		performTimes(LIMIT, ana, () -> get(ME), status().isOk());

		// when
		perform(ana, post(CLASSROOMS).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Turma Girassol\"}"))
			.andExpect(status().isTooManyRequests());

		// then
		clock.setInstant(T0.plusSeconds(60));
		perform(ana, get(CLASSROOMS)).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name").value(not(hasItem("Turma Girassol"))));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM classrooms", Integer.class)).isZero();
	}

	@Test
	@DisplayName(REQUIREMENT + "Janela deslizante libera o usuário")
	void shouldReleaseUserWhenOldestRequestLeavesWindow() throws Exception {
		// given
		perform(ana, get(ME)).andExpect(status().isOk());
		clock.setInstant(T0.plusSeconds(30));
		performTimes(LIMIT - 1, ana, () -> get(ME), status().isOk());

		// when / then
		clock.setInstant(T0.plusSeconds(59));
		perform(ana, get(ME)).andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
		clock.setInstant(T0.plusSeconds(60));
		perform(ana, get(ME)).andExpect(status().isOk());
	}

	@Test
	@DisplayName(REQUIREMENT + "Requisições recusadas não prolongam o bloqueio")
	void shouldNotExtendBlockWhenRefusedRequestsAreRetried() throws Exception {
		// given
		performTimes(LIMIT, ana, () -> get(ME), status().isOk());

		// when
		clock.setInstant(T0.plusSeconds(30));
		performTimes(50, ana, () -> get(ME), status().isTooManyRequests());

		// then
		clock.setInstant(T0.plusSeconds(60));
		perform(ana, get(ME)).andExpect(status().isOk());
	}

	@Test
	@DisplayName(REQUIREMENT + "Erros também contam")
	void shouldCountRequestsWhenTheyFail() throws Exception {
		// given
		performTimes(LIMIT, ana, () -> get("/api/v1/children/00000000-0000-0000-0000-000000000000"),
				status().isNotFound());

		// when / then
		perform(ana, get(ME)).andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName(REQUIREMENT + "Limite não afeta outro usuário")
	void shouldNotAffectOtherUserWhenOneIsBlocked() throws Exception {
		// given
		testUsers.create("Bruno Lima", BRUNO_LOGIN, BRUNO_PASSWORD, "EDUCADOR");
		String bruno = testUsers.bearer(BRUNO_LOGIN, BRUNO_PASSWORD);
		blockAna();

		// when / then
		perform(bruno, get(ME)).andExpect(status().isOk());
	}

	@Test
	@DisplayName(REQUIREMENT + "Token novo do mesmo usuário continua limitado")
	void shouldKeepLimitingUserWhenNewTokenIsIssued() throws Exception {
		// given
		blockAna();

		// when
		String newToken = testUsers.bearer(ANA_LOGIN, ANA_PASSWORD);

		// then
		perform(newToken, get(ME)).andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName(REQUIREMENT + "Rotas públicas fora do limite")
	void shouldNotLimitPublicRoutesWhenUserIsBlocked() throws Exception {
		// given
		blockAna();

		// when / then
		perform(ana, get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	@DisplayName(REQUIREMENT + "Requisição sem token responde 401, não 429")
	void shouldRespondUnauthorizedWhenRequestHasNoToken() throws Exception {
		// given
		blockAna();

		// when / then
		mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Log por requisição sem dados pessoais — Scenario: Requisição recusada pelo limite")
	void shouldLogRefusedRequestWithItsRoute(CapturedOutput output) throws Exception {
		// given
		performTimes(LIMIT, ana, () -> get(ME), status().isOk());
		int start = output.length();

		// when
		perform(ana, get(ME)).andExpect(status().isTooManyRequests());

		// then
		assertThat(output.toString().substring(start).lines()
				.filter(line -> line.contains("http method="))
				.map(line -> line.substring(line.indexOf("http method=")).strip()))
			.singleElement().asString()
			.matches("http method=GET route=/api/v1/auth/me status=429 durationMs=\\d+");
	}

	private void blockAna() throws Exception {
		performTimes(LIMIT, ana, () -> get(ME), status().isOk());
		perform(ana, get(ME)).andExpect(status().isTooManyRequests());
	}

	/** Performs a new request from {@code request} each time, so that headers do not pile up on one builder. */
	private void performTimes(int times, String bearer, Supplier<MockHttpServletRequestBuilder> request,
			ResultMatcher expected) throws Exception {
		for (int i = 0; i < times; i++) {
			perform(bearer, request.get()).andExpect(expected);
		}
	}

	private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

}
