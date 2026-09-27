package com.manuelaalecio.educare_backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUserAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

class JpaAuditingConfigurationTest {

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldProvideCurrentInstantFromClockWhenAskedForNow() {
		// given
		Instant now = Instant.parse("2026-01-10T12:00:00Z");
		Clock clock = Clock.fixed(now, ZoneOffset.UTC);
		DateTimeProvider provider = new JpaAuditingConfiguration().auditingDateTimeProvider(clock);

		// when
		Optional<TemporalAccessor> result = provider.getNow();

		// then
		assertThat(result).contains(now);
	}

	@Test
	void shouldProvideAuthenticatedUserIdWhenAskedForCurrentAuditor() {
		// given
		UUID userId = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
		SecurityContextHolder.getContext()
			.setAuthentication(new AuthenticatedUserAuthentication(new AuthenticatedUser(userId, "ADMIN")));
		AuditorAware<UUID> provider = new JpaAuditingConfiguration().auditingAuditorProvider();

		// when
		Optional<UUID> result = provider.getCurrentAuditor();

		// then
		assertThat(result).contains(userId);
	}

}
