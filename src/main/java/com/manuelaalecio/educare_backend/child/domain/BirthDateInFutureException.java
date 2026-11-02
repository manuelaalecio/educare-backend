package com.manuelaalecio.educare_backend.child.domain;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;

/**
 * The birth date is after the current date in the institution's time zone.
 */
public class BirthDateInFutureException extends InvalidFieldException {

	public BirthDateInFutureException() {
		super("birthDate", "Data de nascimento não pode ser futura");
	}

}
