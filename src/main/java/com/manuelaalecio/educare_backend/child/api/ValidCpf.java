package com.manuelaalecio.educare_backend.child.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated string must be a CPF with one of the accepted formats and right check digits. {@code null} is considered valid.
 */
@Documented
@Constraint(validatedBy = ValidCpfValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCpf {

	String message() default "deve ser um CPF válido";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
