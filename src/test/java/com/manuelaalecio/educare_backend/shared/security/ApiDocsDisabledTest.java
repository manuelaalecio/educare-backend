package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * With springdoc disabled, as outside the {@code dev} profile, the documentation routes are not public: they fall
 * into the main filter chain like any missing route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class ApiDocsDisabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@Test
	void shouldNotCreateDocsFilterChainWhenSpringdocIsDisabled() {
		// when / then
		assertThat(context.getBeanNamesForType(ApiDocsSecurityConfiguration.class)).isEmpty();
	}

	@Test
	void shouldRespondUnauthorizedWhenOpenApiDocumentIsRequestedWithoutToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/v3/api-docs"));

		// then
		result.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRespondUnauthorizedWhenSwaggerUiIsRequestedWithoutToken() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/swagger-ui/index.html"));

		// then
		result.andExpect(status().isUnauthorized());
	}

}
