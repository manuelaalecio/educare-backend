package com.manuelaalecio.educare_backend.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Issues the access tokens of the API. The token carries only {@code iss}, {@code sub} (the user id), {@code iat}
 * and {@code exp}: no personal data, and no role, which is read from the database on every request.
 */
public class AccessTokenIssuer {

	public static final String ISSUER = "educare-backend";

	private final JwtEncoder encoder;
	private final Duration expiration;
	private final Clock clock;

	public AccessTokenIssuer(JwtEncoder encoder, Duration expiration, Clock clock) {
		this.encoder = encoder;
		this.expiration = expiration;
		this.clock = clock;
	}

	public IssuedAccessToken issue(UUID userId) {
		// JWT dates have second precision.
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(userId.toString())
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plus(expiration))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedAccessToken(value, expiration.toSeconds());
	}

}
