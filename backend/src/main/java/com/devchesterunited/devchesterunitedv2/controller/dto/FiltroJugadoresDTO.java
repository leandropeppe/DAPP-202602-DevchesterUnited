package com.devchesterunited.devchesterunitedv2.controller.dto;

import jakarta.validation.ConstraintViolationException;
import com.devchesterunited.devchesterunitedv2.service.dto.FiltroJugadoresConsulta;
import java.util.Set;

/** Entrada validada; null indica filtro o paginación ausente. */
public record FiltroJugadoresDTO(String liga, String equipo, String posicion, Integer pagina, Integer limite)
        implements FiltroJugadoresConsulta {
    public FiltroJugadoresDTO {
        liga = validarFiltro(liga, "league", 100);
        equipo = validarFiltro(equipo, "team", 100);
        posicion = validarFiltro(posicion, "position", 30);
        pagina = pagina == null ? 1 : pagina;
        limite = limite == null ? 20 : limite;
        if (pagina < 1) {
            throw invalido("page debe ser un entero entre 1 y 2147483647.");
        }
        if (limite < 1 || limite > 100) {
            throw invalido("limit debe ser un entero entre 1 y 100.");
        }
        calcularOffset(pagina, limite);
    }

    /** Convierte valores HTTP individuales; multiplicidad y nombres pertenecen al Controller. */
    public static FiltroJugadoresDTO desdeParametros(String liga, String equipo, String posicion,
            String pagina, String limite) {
        return new FiltroJugadoresDTO(liga, equipo, posicion,
                entero(pagina, "page"), entero(limite, "limit"));
    }

    public long offset() {
        return calcularOffset(pagina, limite);
    }

    private static String validarFiltro(String valor, String campo, int maximo) {
        final String prefijoFiltro = "El filtro ";
        if (valor == null) return null;
        if (valor.codePoints().anyMatch(Character::isISOControl)) {
            throw invalido(prefijoFiltro + campo + " no permite caracteres de control.");
        }
        String normalizado = valor.trim();
        if (normalizado.isBlank()) {
            throw invalido(prefijoFiltro + campo + " no puede estar vacío.");
        }
        if (normalizado.codePointCount(0, normalizado.length()) > maximo) {
            throw invalido(prefijoFiltro + campo + " supera la longitud máxima de "
                    + maximo + " caracteres.");
        }

        return normalizado;
    }

    private static Integer entero(String valor, String campo) {
        if (valor == null) return null;
        if (!valor.matches("\\d+")) {
            throw invalido(campo + " debe ser un entero válido.");
        }
        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException exception) {
            throw invalido(campo + " excede el rango de enteros permitido.");
        }
    }

    private static long calcularOffset(int pagina, int limite) {
        try {
            long offset = Math.multiplyExact(Math.subtractExact((long) pagina, 1L), (long) limite);
            // Rango admitido por el contrato de paginación de la aplicación.
            Math.toIntExact(offset);
            return offset;
        } catch (ArithmeticException exception) {
            throw invalido("La paginación excede el rango de offset permitido.");
        }
    }

    private static ConstraintViolationException invalido(String mensaje) {
        return new ConstraintViolationException(mensaje, Set.of());
    }
}
