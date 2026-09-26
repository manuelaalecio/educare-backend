package com.manuelaalecio.educare_backend.shared.error;

/**
 * Base of the domain exceptions for a request that conflicts with the current state; converted to
 * {@code 409 Conflict}. The message becomes the problem {@code detail}, so it must not contain personal data.
 */
public abstract class ConflictException extends RuntimeException {

	protected ConflictException(String message) {
		super(message);
	}

}
