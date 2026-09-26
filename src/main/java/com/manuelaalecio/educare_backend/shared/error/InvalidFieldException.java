package com.manuelaalecio.educare_backend.shared.error;

import lombok.Getter;

/**
 * Base of the exceptions for a request field refused by a rule that Bean Validation cannot check (e.g. a
 * current password that does not match); converted to {@code 400 Bad Request} with an {@code errors} entry for
 * the field, in the same format as the Bean Validation errors. The message must not contain personal data.
 */
@Getter
public abstract class InvalidFieldException extends RuntimeException {

	private final String field;

	protected InvalidFieldException(String field, String message) {
		super(message);
		this.field = field;
	}

}
