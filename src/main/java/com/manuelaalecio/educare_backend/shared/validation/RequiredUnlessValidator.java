package com.manuelaalecio.educare_backend.shared.validation;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Reads the record components by reflection: see {@link RequiredUnless}.
 */
public class RequiredUnlessValidator implements ConstraintValidator<RequiredUnless, Record> {

	private String flag;

	private String[] fields;

	@Override
	public void initialize(RequiredUnless constraint) {
		this.flag = constraint.flag();
		this.fields = constraint.fields();
	}

	@Override
	public boolean isValid(Record value, ConstraintValidatorContext context) {
		if (value == null || Boolean.TRUE.equals(component(value, flag))) {
			return true;
		}
		String[] missing = Arrays.stream(fields).filter(field -> isEmpty(component(value, field))).toArray(String[]::new);
		if (missing.length == 0) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		for (String field : missing) {
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode(field)
				.addConstraintViolation();
		}
		return false;
	}

	private static boolean isEmpty(Object value) {
		return value == null || value instanceof CharSequence text && text.toString().isBlank();
	}

	private static Object component(Record value, String name) {
		RecordComponent component = Arrays.stream(value.getClass().getRecordComponents())
			.filter(candidate -> candidate.getName().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"@RequiredUnless: " + value.getClass().getSimpleName() + " não tem o componente " + name));
		try {
			var accessor = component.getAccessor();
			accessor.setAccessible(true);
			return accessor.invoke(value);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("@RequiredUnless: não foi possível ler o componente " + name, ex);
		}
	}

}
