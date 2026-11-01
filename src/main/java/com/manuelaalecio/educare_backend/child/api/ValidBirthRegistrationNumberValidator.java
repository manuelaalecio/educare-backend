package com.manuelaalecio.educare_backend.child.api;

import com.manuelaalecio.educare_backend.child.domain.BirthRegistrationNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Delegates to the domain rule ({@link BirthRegistrationNumber#isValid(String)}), so the request and the entity agree.
 */
public class ValidBirthRegistrationNumberValidator implements ConstraintValidator<ValidBirthRegistrationNumber, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || BirthRegistrationNumber.isValid(value);
	}

}
