package com.manuelaalecio.educare_backend.shared.security;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a validated token into the {@link AuthenticatedUserAuthentication} of its user, with the role read now from
 * the {@link AuthenticatedUserLookup}. A subject that is not a user id, or a user that no longer exists, rejects the
 * token with a generic message, so the response does not tell why.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String INVALID_TOKEN = "Invalid token";

	private final AuthenticatedUserLookup lookup;

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID userId = parseUserId(jwt.getSubject());
		return lookup.findById(userId)
			.map(AuthenticatedUserAuthentication::new)
			.orElseThrow(() -> new InvalidBearerTokenException(INVALID_TOKEN));
	}

	private static UUID parseUserId(String subject) {
		if (subject == null) {
			throw new InvalidBearerTokenException(INVALID_TOKEN);
		}
		try {
			return UUID.fromString(subject);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidBearerTokenException(INVALID_TOKEN);
		}
	}

}
