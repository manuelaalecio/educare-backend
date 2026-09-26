package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.ConflictException;

/**
 * The login is already used by another user. The message never contains the email (personal data, LGPD).
 */
public class LoginAlreadyInUseException extends ConflictException {

	public LoginAlreadyInUseException() {
		super("Login já está em uso");
	}

}
