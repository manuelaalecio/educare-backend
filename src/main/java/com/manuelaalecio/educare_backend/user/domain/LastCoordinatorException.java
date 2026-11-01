package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * A demotion or deletion would leave the system without any {@link Role#COORDENADOR}. The message never contains the
 * email or the name (personal data, LGPD).
 */
public class LastCoordinatorException extends ConflictException {

	public LastCoordinatorException() {
		super("O sistema precisa ter ao menos um(a) coordenador(a)");
	}

}
