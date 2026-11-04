package com.manuelaalecio.educare_backend.shared.security;

import java.util.OptionalLong;

import com.manuelaalecio.educare_backend.shared.error.TooManyRequestsException;

/**
 * The user made too many requests in the window of the {@link RequestRateLimiter}; the request is refused with
 * {@code 429} and a {@code Retry-After} of when the oldest counted request leaves the window.
 */
public class RequestRateLimitExceededException extends TooManyRequestsException {

	private final long retryAfterSeconds;

	public RequestRateLimitExceededException(long retryAfterSeconds) {
		super("Muitas requisições. Aguarde um instante.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	@Override
	public OptionalLong retryAfterSeconds() {
		return OptionalLong.of(retryAfterSeconds);
	}

}
