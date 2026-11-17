package com.manuelaalecio.educare_backend.guardian.domain;

import com.manuelaalecio.educare_backend.shared.error.FieldConflictException;

/**
 * Another guardian already has the same CPF. The message never repeats the CPF sent (personal data).
 */
public class GuardianCpfAlreadyInUseException extends FieldConflictException {

	public GuardianCpfAlreadyInUseException() {
		super("cpf", "Já existe um responsável com este CPF.");
	}

}
