package com.manuelaalecio.educare_backend.classroom.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * A class with at least one student cannot be deleted.
 */
public class ClassroomHasStudentsException extends ConflictException {

	public ClassroomHasStudentsException() {
		super("A turma tem alunos. Tire os alunos da turma antes de excluí-la");
	}

}
