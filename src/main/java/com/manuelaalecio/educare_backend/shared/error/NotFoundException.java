package com.manuelaalecio.educare_backend.shared.error;

/**
 * Base of the domain exceptions for a resource that does not exist; converted to {@code 404 Not Found}.
 * The message becomes the problem {@code detail}, so it must not contain personal data.
 */
public abstract class NotFoundException extends RuntimeException {

	protected NotFoundException(String message) {
		super(message);
	}

}
