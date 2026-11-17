package com.manuelaalecio.educare_backend.child.domain;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;

/**
 * A guardian sent by id for the child does not exist: a {@code 400} on the item's field (for example
 * {@code guardians[0].guardianId}), not a {@code 404}, because the missing resource is a value of the body.
 */
public class GuardianNotFoundForChildException extends InvalidFieldException {

	public GuardianNotFoundForChildException(String field) {
		super(field, "Responsável não encontrado");
	}

}
