package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;

/**
 * The new password given to change one's own password is the user's login, ignoring case. The message does not
 * repeat the login (personal data).
 */
public class NewPasswordSameAsLoginException extends InvalidFieldException {

	public NewPasswordSameAsLoginException() {
		super("newPassword", "Não pode ser igual ao login");
	}

}
