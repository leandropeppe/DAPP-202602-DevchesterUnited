package com.devchesterunited.devchesterunitedv2.controller.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

public final class UsernameValidator implements ConstraintValidator<UsernameValido, String> {
    private String policy = "strict";
    private int minLength = 3;
    private int maxLength = 30;

    @Value("${app.auth.username-policy:strict}")
    void setPolicy(String value) { policy = value; }

    @Value("${app.auth.username-min-length:3}")
    void setMinLength(int value) { minLength = value; }

    @Value("${app.auth.username-max-length:30}")
    void setMaxLength(int value) { maxLength = value; }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        if (minLength < 1 || maxLength < minLength || maxLength > 255) return false;
        if (value.codePointCount(0, value.length()) < minLength
                || value.codePointCount(0, value.length()) > maxLength) return false;
        if (value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) return false;
        return !"strict".equalsIgnoreCase(policy) || value.matches("[a-z0-9]+");
    }
}
