package com.manuelaalecio.educare_backend.shared.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUserAuditor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auditing of every {@link BaseEntity}: dates from the {@link Clock} and authors from the authenticated user. The
 * auditor is declared here, not in the security configuration, so {@code @DataJpaTest}s that import this class get it
 * without loading Spring Security.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider", auditorAwareRef = "auditingAuditorProvider")
public class JpaAuditingConfiguration {

	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(Instant.now(clock));
	}

	@Bean
	AuditorAware<UUID> auditingAuditorProvider() {
		return new AuthenticatedUserAuditor();
	}

}
