package com.manuelaalecio.educare_backend.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Scenarios of the requirement "Documentação da API só em desenvolvimento" with the {@code dev} profile, the default
 * one in the tests. The scenario outside {@code dev} is in {@link SecurityStartupScenarioTest}, and the routes with
 * springdoc disabled in {@link ApiDocsDisabledTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiDocsScenarioTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Scenario: Documento OpenAPI público no dev")
	void shouldPublishOpenApiDocumentWithoutTokenWhenProfileIsDev() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/v3/api-docs"));

		// then
		result.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.paths['/api/v1/users']").exists())
			.andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].type").value("http"))
			.andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
			.andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].bearerFormat").value("JWT"))
			.andExpect(jsonPath("$.security[0]['bearer-jwt']").exists())
			// the login is public: its empty requirement overrides the bearer token of the whole API
			.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/users'].get.security").doesNotExist());
	}

	@Test
	@DisplayName("Scenario: Swagger UI público no dev")
	void shouldServeSwaggerUiWithoutTokenWhenProfileIsDev() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/swagger-ui/index.html"));

		// then
		result.andExpect(status().isOk());
	}

	@Test
	void shouldRedirectToSwaggerUiWhenShortPathHasNoToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/swagger-ui.html"));

		// then
		result.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/swagger-ui/index.html"));
	}

}
