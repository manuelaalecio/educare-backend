package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.security.SecurityProperties.Cors;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtConfigurationTest {

	private static final String SECRET = "jwt-configuration-test-secret-0123456789";
	private static final String OTHER_SECRET = "another-secret-not-known-by-the-backend-0123";
	private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	private final JwtConfiguration configuration = new JwtConfiguration();
	private final MutableClock clock = new MutableClock(NOW);
	private final SecurityProperties properties = properties(SECRET);
	private final JwtEncoder encoder = configuration.jwtEncoder(properties);
	private final JwtDecoder decoder = configuration.jwtDecoder(properties, clock);
	private final AccessTokenIssuer issuer = configuration.accessTokenIssuer(encoder, properties, clock);

	private static SecurityProperties properties(String secret) {
		return new SecurityProperties(new SecurityProperties.Jwt(secret, Duration.ofHours(8)),
			new Cors(List.of("http://localhost:5173")));
	}

	@Test
	void shouldDecodeTokenWhenIssuedWithConfiguredKey() {
		// given
		String token = issuer.issue(USER_ID).value();

		// when
		Jwt jwt = decoder.decode(token);

		// then
		assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(AccessTokenIssuer.ISSUER);
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
	}

	@Test
	void shouldAcceptTokenWhenOneMinuteBeforeExpiration() {
		// given
		String token = issuer.issue(USER_ID).value();
		clock.setInstant(NOW.plus(Duration.ofHours(8)).minus(Duration.ofMinutes(1)));

		// when
		Jwt jwt = decoder.decode(token);

		// then
		assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
	}

	@Test
	void shouldRejectTokenWhenOneMinuteAfterExpiration() {
		// given
		String token = issuer.issue(USER_ID).value();
		clock.setInstant(NOW.plus(Duration.ofHours(8)).plus(Duration.ofMinutes(1)));

		// when / then
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
	}

	@Test
	void shouldRejectTokenWhenSignedWithAnotherKey() {
		// given: a token valid in every other way
		JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(properties(OTHER_SECRET).jwt().secretKey()));
		String token = new AccessTokenIssuer(otherEncoder, Duration.ofHours(8), clock).issue(USER_ID).value();

		// when / then
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
	}

	@Test
	void shouldRejectTokenWhenIssuerIsDifferent() {
		// given
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("another-issuer")
			.subject(USER_ID.toString())
			.issuedAt(NOW)
			.expiresAt(NOW.plus(Duration.ofHours(8)))
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();

		// when / then
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
	}

	@Test
	void shouldRejectTokenWhenMalformed() {
		assertThatThrownBy(() -> decoder.decode("abc.def")).isInstanceOf(BadJwtException.class);
	}

	@Test
	void shouldCreateBeansWhenPropertiesAreBoundFromConfiguration() {
		new ApplicationContextRunner()
			.withUserConfiguration(JwtConfiguration.class)
			.withBean(Clock.class, () -> clock)
			.withPropertyValues(
				"educare.security.jwt.secret=" + SECRET,
				"educare.security.jwt.expiration=8h",
				"educare.security.cors.allowed-origins=https://a.org,http://localhost:5173")
			.run(context -> {
				// then
				assertThat(context).hasNotFailed();
				SecurityProperties bound = context.getBean(SecurityProperties.class);
				assertThat(bound.jwt().expiration()).isEqualTo(Duration.ofHours(8));
				assertThat(bound.cors().allowedOrigins()).containsExactly("https://a.org", "http://localhost:5173");
				IssuedAccessToken token = context.getBean(AccessTokenIssuer.class).issue(USER_ID);
				assertThat(token.expiresIn()).isEqualTo(28_800);
				assertThat(context.getBean(JwtDecoder.class).decode(token.value()).getSubject())
					.isEqualTo(USER_ID.toString());
			});
	}

	@Test
	void shouldFailStartupWhenSecretPlaceholderIsUnresolved() {
		new ApplicationContextRunner()
			.withUserConfiguration(JwtConfiguration.class)
			.withBean(Clock.class, () -> clock)
			.withPropertyValues(
				"educare.security.jwt.secret=${EDUCARE_UNDEFINED_TEST_SECRET}",
				"educare.security.jwt.expiration=8h",
				"educare.security.cors.allowed-origins=http://localhost:5173")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("variável não resolvida"));
	}

	@Test
	void shouldFailStartupWhenOriginsAreEmpty() {
		// given: what Compose passes when the variable is missing from .env
		new ApplicationContextRunner()
			.withUserConfiguration(JwtConfiguration.class)
			.withBean(Clock.class, () -> clock)
			.withPropertyValues(
				"educare.security.jwt.secret=" + SECRET,
				"educare.security.jwt.expiration=8h",
				"educare.security.cors.allowed-origins=")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("educare.security.cors.allowed-origins"));
	}

}
