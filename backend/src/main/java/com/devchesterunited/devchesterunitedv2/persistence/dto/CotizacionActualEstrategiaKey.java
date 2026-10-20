package com.devchesterunited.devchesterunitedv2.persistence.dto;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CotizacionActualEstrategiaKey implements Serializable {
    private UUID jugadorId;
    private String estrategiaId;
    private int estrategiaVersion;
    public CotizacionActualEstrategiaKey() {
        // Constructor requerido por JPA para reconstruir la clave compuesta.
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CotizacionActualEstrategiaKey k)) return false;
        return estrategiaVersion == k.estrategiaVersion
                && Objects.equals(jugadorId, k.jugadorId)
                && Objects.equals(estrategiaId, k.estrategiaId);
    }

    @Override
    public int hashCode() { return Objects.hash(jugadorId, estrategiaId, estrategiaVersion); }
}
