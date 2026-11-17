package com.manuelaalecio.educare_backend.guardian.domain;

import com.manuelaalecio.educare_backend.shared.error.NotFoundException;

public class GuardianNotFoundException extends NotFoundException {

	public GuardianNotFoundException() {
		super("Responsável não encontrado");
	}

}
