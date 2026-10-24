package com.devchesterunited.devchesterunitedv2.service.dto.shared;

public record LoginResultado(String tokenAcceso) {
    @Override
    public String toString() {
        return "LoginResultado[token omitido]";
    }
}
