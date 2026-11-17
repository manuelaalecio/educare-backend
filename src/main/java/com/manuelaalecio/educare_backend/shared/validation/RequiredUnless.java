package com.manuelaalecio.educare_backend.shared.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level constraint for records: unless the {@link #flag()} component is {@code true}, each of the
 * {@link #fields()} components is required. Every missing field ({@code null}, or a blank text) gets its own
 * violation on that field, so the errors come together with the other ones, each on its own path (for example
 * {@code workplace}, or {@code guardians[0].guardian.workplace} when the record is nested).
 * <p>
 * A flag or field name that is not a component of the record is a configuration error, reported as a
 * {@link jakarta.validation.ValidationException} when the record is validated.
 */
@Documented
@Constraint(validatedBy = RequiredUnlessValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredUnless {

	/**
	 * The {@link Boolean} component that dispenses the fields when {@code true}; {@code null} counts as
	 * {@code false}.
	 */
	String flag();

	/**
	 * The components required when the flag is not {@code true}.
	 */
	String[] fields();

	String message() default "é obrigatório";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
