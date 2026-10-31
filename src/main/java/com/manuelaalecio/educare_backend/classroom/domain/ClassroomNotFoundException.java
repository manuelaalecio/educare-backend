package com.manuelaalecio.educare_backend.classroom.domain;

import com.manuelaalecio.educare_backend.shared.error.NotFoundException;

public class ClassroomNotFoundException extends NotFoundException {

	public ClassroomNotFoundException() {
		super("Turma não encontrada");
	}

}
