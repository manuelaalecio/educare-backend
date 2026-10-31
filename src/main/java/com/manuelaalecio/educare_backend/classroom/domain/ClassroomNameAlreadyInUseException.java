package com.manuelaalecio.educare_backend.classroom.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * Another class already has the same name, ignoring case and accents. The message never repeats the name sent.
 */
public class ClassroomNameAlreadyInUseException extends ConflictException {

	public ClassroomNameAlreadyInUseException() {
		super("Já existe uma turma com esse nome");
	}

}
