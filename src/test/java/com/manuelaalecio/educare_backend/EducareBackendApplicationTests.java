package com.manuelaalecio.educare_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EducareBackendApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
	}

	/**
	 * Tests activate no profile and rely on {@code dev} being the default (e.g. for the admin password of V3);
	 * {@code prod} is only activated explicitly, by docker-compose.yaml.
	 */
	@Test
	void shouldUseDevProfileWhenNoProfileIsActive() {
		assertThat(environment.getActiveProfiles()).isEmpty();
		assertThat(environment.getDefaultProfiles()).containsExactly("dev");
	}

}
