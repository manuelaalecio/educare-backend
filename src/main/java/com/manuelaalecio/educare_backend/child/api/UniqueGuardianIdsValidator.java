package com.manuelaalecio.educare_backend.child.api;

import java.util.List;
import java.util.Objects;

import com.manuelaalecio.educare_backend.child.api.dto.ChildGuardianRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UniqueGuardianIdsValidator implements ConstraintValidator<UniqueGuardianIds, List<ChildGuardianRequest>> {

	@Override
	public boolean isValid(List<ChildGuardianRequest> value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		List<?> ids = value.stream().filter(Objects::nonNull).map(ChildGuardianRequest::guardianId)
			.filter(Objects::nonNull)
			.toList();
		return ids.stream().distinct().count() == ids.size();
	}

}
