package com.manuelaalecio.educare_backend.shared.error;

import lombok.Getter;

/**
 * Base of the conflicts caused by the value of one request field (e.g. a document already used by another record);
 * converted to {@code 409 Conflict} with the message as the {@code detail} and an {@code errors} entry for the
 * field, so the client can show the error next to it. The message must not contain personal data.
 */
@Getter
public abstract class FieldConflictException extends ConflictException {

	private final String field;

	protected FieldConflictException(String field, String message) {
		super(message);
		this.field = field;
	}

	/**
	 * The same conflict anchored inside an item of the body: for a conflict on {@code cpf}, the prefix
	 * {@code guardians[1].guardian.} gives the field {@code guardians[1].guardian.cpf}, with the same message.
	 *
	 * @return a new exception, with this one as its cause
	 */
	public FieldConflictException withFieldPrefix(String prefix) {
		return new NestedFieldConflictException(prefix + field, getMessage(), this);
	}

}
