package com.devchesterunited.devchesterunitedv2.persistence.dto;

import java.io.Serializable;
import java.util.UUID;

public class ResultadoElegibilidadValorizacionKey implements Serializable {
    private UUID ejecucionId;
    private UUID jugadorId;
    private String estrategiaId;
    private int estrategiaVersion;
    public ResultadoElegibilidadValorizacionKey() { }
    @Override public boolean equals(Object o) { if (!(o instanceof ResultadoElegibilidadValorizacionKey k)) return false; return estrategiaVersion == k.estrategiaVersion && java.util.Objects.equals(ejecucionId,k.ejecucionId) && java.util.Objects.equals(jugadorId,k.jugadorId) && java.util.Objects.equals(estrategiaId,k.estrategiaId); }
    @Override public int hashCode() { return java.util.Objects.hash(ejecucionId, jugadorId, estrategiaId, estrategiaVersion); }
}
