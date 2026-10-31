package com.manuelaalecio.educare_backend.shared.security;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Scenario of the requirement "Actuator restrito ao health, sem detalhes" in which the application refuses traffic,
 * as at the start of a shutdown. It changes the readiness state of the context, so the context is discarded after the
 * test instead of being shared with the other scenario tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReadinessScenarioTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("Scenario: Readiness recusa tráfego sem derrubar a liveness")
	void shouldRespondServiceUnavailableOnReadinessAndUpOnLivenessWhenApplicationRefusesTraffic() throws Exception {
		// given
		AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);

		// when
		ResultActions readiness = mockMvc.perform(get("/actuator/health/readiness"));
		ResultActions liveness = mockMvc.perform(get("/actuator/health/liveness"));

		// then
		readiness.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
				.andExpect(jsonPath("$.*", hasSize(1)));
		liveness.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.*", hasSize(1)));
	}

}
