package com.manuelaalecio.educare_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetailsService;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EducareBackendApplicationTests {

	@Autowired
	private Environment environment;

	@Autowired
	private ApplicationContext context;

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

	/**
	 * Authentication is by bearer token only: no {@link UserDetailsService} is auto-configured, so the startup does
	 * not log "Using generated security password".
	 */
	@Test
	void shouldNotConfigureUserDetailsServiceWhenApplicationStarts() {
		assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}

}
