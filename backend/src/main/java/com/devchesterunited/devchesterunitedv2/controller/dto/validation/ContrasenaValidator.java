package com.devchesterunited.devchesterunitedv2.controller.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public final class ContrasenaValidator implements ConstraintValidator<ContrasenaValida, String> {
    private boolean registro;

    @Override
    public void initialize(ContrasenaValida regla) { registro = regla.registro(); }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        if (valor == null) return true; // La obligatoriedad corresponde a @NotNull.
        int longitud = valor.codePointCount(0, valor.length());
        if (longitud < (registro ? 8 : 1) || longitud > 72
                || valor.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        if (!registro) return true;
        if (valor.codePoints().anyMatch(Character::isISOControl)
                || espacio(valor.codePointAt(0)) || espacio(valor.codePointBefore(valor.length()))) return false;
        return valor.chars().anyMatch(c -> c >= 'A' && c <= 'Z')
                && valor.chars().anyMatch(c -> c >= 'a' && c <= 'z')
                && valor.chars().anyMatch(c -> c >= '0' && c <= '9');
    }

    private static boolean espacio(int caracter) {
        return Character.isWhitespace(caracter) || Character.isSpaceChar(caracter);
    }
}
