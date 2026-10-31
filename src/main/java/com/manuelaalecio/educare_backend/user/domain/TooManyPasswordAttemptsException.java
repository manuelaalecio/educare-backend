package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.TooManyRequestsException;

/**
 * The user got the current password wrong too many times recently, so changing one's own password is refused for
 * a while, even with the right password.
 */
public class TooManyPasswordAttemptsException extends TooManyRequestsException {

	public TooManyPasswordAttemptsException() {
		super("Muitas tentativas. Tente novamente em alguns minutos.");
	}

}
