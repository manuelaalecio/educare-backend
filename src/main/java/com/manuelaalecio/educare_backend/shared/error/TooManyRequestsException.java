package com.manuelaalecio.educare_backend.shared.error;

/**
 * Base of the exceptions for a request refused because too many attempts were made recently; converted to
 * {@code 429 Too Many Requests}. The message becomes the problem {@code detail}, so it must not contain personal data.
 */
public abstract class TooManyRequestsException extends RuntimeException {

	protected TooManyRequestsException(String message) {
		super(message);
	}

}
