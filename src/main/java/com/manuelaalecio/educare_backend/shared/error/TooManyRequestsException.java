package com.manuelaalecio.educare_backend.shared.error;

import java.util.OptionalLong;

/**
 * Base of the exceptions for a request refused because too many attempts were made recently; converted to
 * {@code 429 Too Many Requests}. The message becomes the problem {@code detail}, so it must not contain personal data.
 * A subclass that knows when the client can try again overrides {@link #retryAfterSeconds()}, which becomes the
 * {@code Retry-After} header.
 */
public abstract class TooManyRequestsException extends RuntimeException {

	protected TooManyRequestsException(String message) {
		super(message);
	}

	/**
	 * @return the seconds until the client can try again, or empty when unknown (no {@code Retry-After} header)
	 */
	public OptionalLong retryAfterSeconds() {
		return OptionalLong.empty();
	}

}
