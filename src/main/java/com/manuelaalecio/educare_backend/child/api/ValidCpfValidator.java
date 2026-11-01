package com.manuelaalecio.educare_backend.child.api;

import com.manuelaalecio.educare_backend.child.domain.Cpf;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Delegates to the domain rule ({@link Cpf#isValid(String)}), so the request and the entity agree.
 */
public class ValidCpfValidator implements ConstraintValidator<ValidCpf, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || Cpf.isValid(value);
	}

}
