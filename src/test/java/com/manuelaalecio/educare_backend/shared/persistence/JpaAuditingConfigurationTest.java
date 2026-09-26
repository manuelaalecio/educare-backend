package com.manuelaalecio.educare_backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class JpaAuditingConfigurationTest {

	@Test
	void shouldProvideCurrentInstantFromClockWhenAskedForNow() {
		// given
		Instant now = Instant.parse("2026-01-10T12:00:00Z");
		Clock clock = Clock.fixed(now, ZoneOffset.UTC);
		DateTimeProvider provider = new JpaAuditingConfiguration().auditingDateTimeProvider(clock);

		// when
		Optional<TemporalAccessor> result = provider.getNow();

		// then
		assertThat(result).contains(now.plusSeconds(1));
	}

}
