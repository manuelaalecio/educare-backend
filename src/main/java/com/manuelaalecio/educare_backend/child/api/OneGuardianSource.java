package com.manuelaalecio.educare_backend.child.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated guardian item must have exactly one of {@code guardianId} and {@code guardian}. The violation is on
 * the item itself (for example {@code guardians[0]}). {@code null} is considered valid.
 */
@Documented
@Constraint(validatedBy = OneGuardianSourceValidator.class)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OneGuardianSource {

	String message() default "informe guardianId (responsável já cadastrado) ou guardian (responsável novo), e só um deles";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
