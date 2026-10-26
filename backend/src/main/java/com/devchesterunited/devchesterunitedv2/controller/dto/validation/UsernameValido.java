package com.devchesterunited.devchesterunitedv2.controller.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = UsernameValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface UsernameValido {
    String message() default "El nombre de usuario no cumple la política de caracteres configurada.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
