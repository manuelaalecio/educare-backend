package com.manuelaalecio.educare_backend.auth.domain;

import com.manuelaalecio.educare_backend.shared.error.UnauthorizedException;

/**
 * The login does not exist or the password is wrong. The same message is used for both cases and never contains the
 * login (personal data, LGPD).
 */
public class InvalidCredentialsException extends UnauthorizedException {

	public InvalidCredentialsException() {
		super("Login ou senha inválidos");
	}

}
