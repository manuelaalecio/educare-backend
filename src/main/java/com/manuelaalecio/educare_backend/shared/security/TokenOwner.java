package com.manuelaalecio.educare_backend.shared.security;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * The current state of the user of a token, as given by the {@link AuthenticatedUserLookup}: the principal of the
 * request and what decides whether the token is still valid for them.
 *
 * @param passwordChangedAt the last password change, in whole seconds like the {@code iat} of the tokens;
 *        {@code null} when the password was never changed
 */
public record TokenOwner(AuthenticatedUser user, @Nullable Instant passwordChangedAt) {

	/**
	 * A token issued before the last password change is no longer valid; one issued in the same second (such as the
	 * token returned by the change itself) still is.
	 */
	public boolean accepts(@Nullable Instant issuedAt) {
		if (passwordChangedAt == null) {
			return true;
		}
		return issuedAt != null && !issuedAt.isBefore(passwordChangedAt);
	}

}
