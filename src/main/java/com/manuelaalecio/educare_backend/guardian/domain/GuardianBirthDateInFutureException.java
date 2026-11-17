package com.manuelaalecio.educare_backend.guardian.domain;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;

/**
 * The birth date of the guardian is after the current date in the institution's time zone.
 */
public class GuardianBirthDateInFutureException extends InvalidFieldException {

	public GuardianBirthDateInFutureException() {
		super("birthDate", "Data de nascimento não pode ser futura");
	}

}
