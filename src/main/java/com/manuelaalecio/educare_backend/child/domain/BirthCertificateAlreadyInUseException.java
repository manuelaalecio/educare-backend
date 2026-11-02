package com.manuelaalecio.educare_backend.child.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * Another child already has the same document. The message never repeats the birth certificate number sent (personal data).
 */
public class BirthCertificateAlreadyInUseException extends ConflictException {

	public BirthCertificateAlreadyInUseException() {
		super("Matrícula da certidão já cadastrada");
	}

}
