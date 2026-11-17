package com.manuelaalecio.educare_backend.child.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated list of guardian items must not repeat a {@code guardianId}. New guardians and {@code null} items
 * are ignored; a {@code null} list is considered valid.
 */
@Documented
@Constraint(validatedBy = UniqueGuardianIdsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueGuardianIds {

	String message() default "O mesmo responsável não pode ser informado mais de uma vez.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
