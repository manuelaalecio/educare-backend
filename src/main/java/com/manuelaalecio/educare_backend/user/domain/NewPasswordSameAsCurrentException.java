package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;

/**
 * The new password given to change one's own password is the current password.
 */
public class NewPasswordSameAsCurrentException extends InvalidFieldException {

	public NewPasswordSameAsCurrentException() {
		super("newPassword", "Deve ser diferente da senha atual");
	}

}
