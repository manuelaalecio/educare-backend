package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * A user tried to delete their own account. The message never contains the email or the name (personal data, LGPD).
 */
public class SelfDeletionException extends ConflictException {

	public SelfDeletionException() {
		super("Não é permitido excluir o próprio usuário");
	}

}
