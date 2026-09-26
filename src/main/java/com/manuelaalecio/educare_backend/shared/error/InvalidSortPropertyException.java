package com.manuelaalecio.educare_backend.shared.error;

/**
 * A listing was asked to sort by a property that is not allowed; converted to {@code 400 Bad Request}.
 */
public class InvalidSortPropertyException extends RuntimeException {

	public InvalidSortPropertyException(String property) {
		super("Ordenação não permitida pela propriedade '" + property + "'");
	}

}
