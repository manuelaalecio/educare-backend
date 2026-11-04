package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class RateLimitPropertiesTest {

	private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

	@Test
	void shouldDefineProductionDefaultsWhenReadingMainApplicationYaml() throws Exception {
		// given: the main file, not the one in classpath:/config/ that the tests use
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application",
				new ClassPathResource("application.yaml"));
		Binder binder = new Binder(ConfigurationPropertySources.from(sources));

		// when
		RateLimitProperties properties = binder.bind("educare.rate-limit", RateLimitProperties.class).get();

		// then
		assertThat(properties.maxRequests()).isEqualTo(120);
		assertThat(properties.window()).isEqualTo(ONE_MINUTE);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	void shouldRejectMaxRequestsWhenNotPositive(int maxRequests) {
		assertThatIllegalArgumentException().isThrownBy(() -> new RateLimitProperties(maxRequests, ONE_MINUTE))
			.withMessage("educare.rate-limit.max-requests deve ser positivo");
	}

	@ParameterizedTest
	@ValueSource(strings = {"PT0S", "PT-1S"})
	void shouldRejectWindowWhenNotPositive(String window) {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RateLimitProperties(120, Duration.parse(window)))
			.withMessage("educare.rate-limit.window deve ser positivo");
	}

	@Test
	void shouldRejectWindowWhenMissing() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RateLimitProperties(120, null))
			.withMessage("educare.rate-limit.window deve ser positivo");
	}

	@Test
	void shouldAcceptPositiveValues() {
		// when
		RateLimitProperties properties = new RateLimitProperties(1, Duration.ofSeconds(1));

		// then
		assertThat(properties.maxRequests()).isEqualTo(1);
		assertThat(properties.window()).isEqualTo(Duration.ofSeconds(1));
	}

}
