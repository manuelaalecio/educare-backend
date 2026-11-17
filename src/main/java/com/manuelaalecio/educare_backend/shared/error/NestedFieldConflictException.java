package com.manuelaalecio.educare_backend.shared.error;

/**
 * A {@link FieldConflictException} re-anchored inside an item of the body; see
 * {@link FieldConflictException#withFieldPrefix(String)}.
 */
public class NestedFieldConflictException extends FieldConflictException {

	NestedFieldConflictException(String field, String message, FieldConflictException cause) {
		super(field, message);
		initCause(cause);
	}

}
