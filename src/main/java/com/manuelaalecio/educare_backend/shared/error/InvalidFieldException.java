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

	/**
	 * The same error anchored inside an item of the body: for an error on {@code cpf}, the prefix
	 * {@code guardians[1].guardian.} gives the field {@code guardians[1].guardian.cpf}, with the same message.
	 *
	 * @return a new exception, with this one as its cause
	 */
	public InvalidFieldException withFieldPrefix(String prefix) {
		return new NestedInvalidFieldException(prefix + field, getMessage(), this);
	}

}
