package com.manuelaalecio.educare_backend.child.api;

import com.manuelaalecio.educare_backend.child.api.dto.ChildGuardianRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OneGuardianSourceValidator implements ConstraintValidator<OneGuardianSource, ChildGuardianRequest> {

	@Override
	public boolean isValid(ChildGuardianRequest value, ConstraintValidatorContext context) {
		return value == null || (value.guardianId() == null) != (value.guardian() == null);
	}

}
