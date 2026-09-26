package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.security.SecurityProperties.Cors;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AccessTokenIssuerTest {

	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	private final JwtConfiguration configuration = new JwtConfiguration();
	private final MutableClock clock = new MutableClock(Instant.parse("2026-01-10T12:00:00.750Z"));
	private final SecurityProperties properties = new SecurityProperties(
		new SecurityProperties.Jwt("access-token-issuer-test-secret-0123456789", Duration.ofHours(8)),
		new Cors(List.of("http://localhost:5173")));
	private final JwtEncoder encoder = configuration.jwtEncoder(properties);
	private final JwtDecoder decoder = configuration.jwtDecoder(properties, clock);
	private final AccessTokenIssuer issuer = new AccessTokenIssuer(encoder, properties.jwt().expiration(), clock);

	@Test
	void shouldReturnExpirationInSecondsWhenIssuingToken() {
		// when
		IssuedAccessToken token = issuer.issue(USER_ID);

		// then
		assertThat(token.expiresIn()).isEqualTo(28_800);
		assertThat(token.value()).isNotBlank();
	}

	@Test
	void shouldCarryOnlyIssuerSubjectAndDatesWhenIssuingToken() {
		// when
		Jwt jwt = decoder.decode(issuer.issue(USER_ID).value());

		// then: no personal data and no role
		assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("educare-backend");
		assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
	}

	@Test
	void shouldUseClockTruncatedToSecondsWhenSettingDates() {
		// when
		Jwt jwt = decoder.decode(issuer.issue(USER_ID).value());

		// then
		assertThat(jwt.getIssuedAt()).isEqualTo(Instant.parse("2026-01-10T12:00:00Z"));
		assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse("2026-01-10T20:00:00Z"));
	}

}
