package com.manuelaalecio.educare_backend.child.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * Another child already has the same document. The message never repeats the RG sent (personal data).
 */
public class RgAlreadyInUseException extends ConflictException {

	public RgAlreadyInUseException() {
		super("RG já cadastrado");
	}

}
