package com.manuelaalecio.educare_backend.shared.error;

/**
 * An {@link InvalidFieldException} re-anchored inside an item of the body; see
 * {@link InvalidFieldException#withFieldPrefix(String)}.
 */
public class NestedInvalidFieldException extends InvalidFieldException {

	NestedInvalidFieldException(String field, String message, InvalidFieldException cause) {
		super(field, message);
		initCause(cause);
	}

}
