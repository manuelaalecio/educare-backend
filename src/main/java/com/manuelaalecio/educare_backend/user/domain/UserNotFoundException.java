package com.manuelaalecio.educare_backend.user.domain;

import com.manuelaalecio.educare_backend.shared.error.NotFoundException;

public class UserNotFoundException extends NotFoundException {

	public UserNotFoundException() {
		super("Usuário não encontrado");
	}

}
