package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;

/**
 * The current password given to change one's own password does not match the stored one.
 */
public class InvalidCurrentPasswordException extends InvalidFieldException {

	public InvalidCurrentPasswordException() {
		super("currentPassword", "Senha atual incorreta");
	}

}
