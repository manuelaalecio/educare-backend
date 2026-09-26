package com.manuelaalecio.educare_backend.shared.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates users straight in the database and logs them in through the real API, for scenario tests
 * ({@code @SpringBootTest} with {@code @AutoConfigureMockMvc}). Import it with {@code @Import(TestUsers.class)}:
 *
 * <pre>
 * UUID zelia = testUsers.create("Zélia", "zelia@educare.org", "segredo123", "ADMIN");
 * String bearer = testUsers.bearer("zelia@educare.org", "segredo123");
 * mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer));
 * </pre>
 */
@TestComponent
public class TestUsers {

	private static final String LOGIN_PATH = "/api/v1/auth/login";

	private final JdbcTemplate jdbcTemplate;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final MockMvc mockMvc;
	private final JsonMapper jsonMapper;

	public TestUsers(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, Clock clock, MockMvc mockMvc,
			JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.mockMvc = mockMvc;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Inserts a user with the password hashed by the application encoder and the audit dates from the clock.
	 *
	 * @param role {@code ADMIN} or {@code USER}
	 * @return the id of the new user
	 */
	public UUID create(String name, String login, String password, String role) {
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		jdbcTemplate.update("""
				INSERT INTO users (id, name, login, password_hash, role, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, name, login.toLowerCase(Locale.ROOT), passwordEncoder.encode(password), role, now, now);
		return id;
	}

	/**
	 * Logs in with {@code POST /api/v1/auth/login} and returns the access token, failing unless the login
	 * responds {@code 200 OK}.
	 */
	public String login(String login, String password) throws Exception {
		String body = jsonMapper.writeValueAsString(new LoginBody(login, password));
		String response = mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	/**
	 * Same as {@link #login(String, String)}, as the value of the {@code Authorization} header.
	 */
	public String bearer(String login, String password) throws Exception {
		return "Bearer " + login(login, password);
	}

	private record LoginBody(String login, String password) {
	}

}
