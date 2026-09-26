package com.manuelaalecio.educare_backend.shared.error;

/**
 * Base of the exceptions for a request whose credentials were refused; converted to {@code 401 Unauthorized}.
 * The message becomes the problem {@code detail}, so it must not contain personal data.
 */
public abstract class UnauthorizedException extends RuntimeException {

	protected UnauthorizedException(String message) {
		super(message);
	}

}
