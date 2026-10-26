package com.devchesterunited.devchesterunitedv2.controller.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = ContrasenaValidator.class)
public @interface ContrasenaValida {
    String message() default "La contraseña no cumple los requisitos de formato y longitud.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    boolean registro() default false;
}
