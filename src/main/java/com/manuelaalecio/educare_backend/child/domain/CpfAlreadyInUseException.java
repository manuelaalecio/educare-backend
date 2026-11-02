package com.manuelaalecio.educare_backend.child.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * Another child already has the same document. The message never repeats the CPF sent (personal data).
 */
public class CpfAlreadyInUseException extends ConflictException {

	public CpfAlreadyInUseException() {
		super("CPF já cadastrado");
	}

}
