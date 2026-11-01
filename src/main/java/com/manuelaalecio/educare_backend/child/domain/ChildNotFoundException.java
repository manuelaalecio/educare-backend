package com.manuelaalecio.educare_backend.child.domain;

import com.manuelaalecio.educare_backend.shared.error.NotFoundException;

public class ChildNotFoundException extends NotFoundException {

	public ChildNotFoundException() {
		super("Criança não encontrada");
	}

}
