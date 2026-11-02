package com.manuelaalecio.educare_backend.child.domain;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;

/**
 * The class sent for the child does not exist (or was deleted meanwhile): a {@code 400} on the field, not a
 * {@code 404}, because the missing resource is a value of the body.
 */
public class ClassroomNotFoundForChildException extends InvalidFieldException {

	public ClassroomNotFoundForChildException() {
		super("classroomId", "Turma não encontrada");
	}

}
