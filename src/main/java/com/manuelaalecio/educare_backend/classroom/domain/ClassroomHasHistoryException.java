package com.manuelaalecio.educare_backend.classroom.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * A class that some child has already left cannot be deleted, to keep the children's class history.
 */
public class ClassroomHasHistoryException extends ConflictException {

	public ClassroomHasHistoryException() {
		super("A turma tem histórico de crianças e não pode ser excluída");
	}

}
