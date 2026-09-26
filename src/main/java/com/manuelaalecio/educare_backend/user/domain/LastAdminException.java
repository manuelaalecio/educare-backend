package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * A demotion or deletion would leave the system without any {@link Role#ADMIN}. The message never contains the
 * email or the name (personal data, LGPD).
 */
public class LastAdminException extends ConflictException {

	public LastAdminException() {
		super("O sistema precisa ter ao menos um usuário ADMIN");
	}

}
