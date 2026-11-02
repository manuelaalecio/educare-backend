package com.manuelaalecio.educare_backend.child.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated string must be a birth certificate number with exactly 32 digits once dots, spaces and hyphens are removed. {@code null} is considered valid.
 */
@Documented
@Constraint(validatedBy = ValidBirthRegistrationNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBirthRegistrationNumber {

	String message() default "deve ter 32 dígitos";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
