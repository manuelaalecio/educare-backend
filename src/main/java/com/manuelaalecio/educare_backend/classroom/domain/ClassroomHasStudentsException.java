package com.manuelaalecio.educare_backend.classroom.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * A class with at least one active child cannot be deleted.
 */
public class ClassroomHasStudentsException extends ConflictException {

	public ClassroomHasStudentsException() {
		super("A turma tem crianças. Tire as crianças da turma antes de excluí-la");
	}

}
