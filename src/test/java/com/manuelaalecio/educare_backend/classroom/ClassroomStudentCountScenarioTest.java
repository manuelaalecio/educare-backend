package com.manuelaalecio.educare_backend.classroom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomStudentCounter;
import com.manuelaalecio.educare_backend.classroom.application.StudentCounts;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The scenarios of the {@code classroom} spec that need students. Children cannot be linked to classes until
 * enrollment exists, so the {@link ClassroomStudentCounter} is replaced by a mock (design D6); HTTP, security, the
 * service and the database are real. The enrollment change replaces the mock with real students.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ClassroomStudentCountScenarioTest {

	private static final String CLASSROOMS = "/api/v1/classrooms";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String ADMIN_LOGIN = "zelia@educare.org";
	private static final String PASSWORD = "segredo123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TestUsers testUsers;

	@MockitoBean
	private ClassroomStudentCounter studentCounter;

	private String adminBearer;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("DELETE FROM classrooms");
		jdbcTemplate.update("DELETE FROM users WHERE login = ?", ADMIN_LOGIN);
		testUsers.create("Zélia", ADMIN_LOGIN, PASSWORD, "COORDENADOR");
		adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);
		given(studentCounter.countFor(any(UUID.class))).willReturn(StudentCounts.ZERO);
	}

	@Test
	@DisplayName("Scenario: Turma com alunos não é excluída")
	void shouldRejectDeletionWhenClassroomHasStudents() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");
		given(studentCounter.countFor(id)).willReturn(new StudentCounts(2, 1, 1));

		// when
		ResultActions result = mockMvc.perform(delete(CLASSROOMS + "/" + id)
				.header(HttpHeaders.AUTHORIZATION, adminBearer));

		// then
		result.andExpect(status().isConflict()).andExpect(content().contentType(PROBLEM_JSON));
		getClassroom(id).andExpect(status().isOk()).andExpect(jsonPath("$.studentCount").value(2));
	}

	@Test
	@DisplayName("Scenario: Aluno de outro gênero conta só no total")
	void shouldCountStudentOfAnotherGenderOnlyInTotal() throws Exception {
		// given
		UUID id = createClassroom("Turma 1");
		given(studentCounter.countFor(id)).willReturn(new StudentCounts(3, 1, 1));

		// when
		ResultActions result = getClassroom(id);

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.studentCount").value(3))
				.andExpect(jsonPath("$.maleCount").value(1))
				.andExpect(jsonPath("$.femaleCount").value(1));
	}

	private UUID createClassroom(String name) throws Exception {
		String response = mockMvc.perform(post(CLASSROOMS).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"" + name + "\"}")
						.header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.id"));
	}

	private ResultActions getClassroom(UUID id) throws Exception {
		return mockMvc.perform(get(CLASSROOMS + "/" + id).header(HttpHeaders.AUTHORIZATION, adminBearer));
	}

}
