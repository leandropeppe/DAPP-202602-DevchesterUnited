package com.devchesterunited.devchesterunitedv2.persistence.dto;

import java.io.Serializable;
import java.util.UUID;

public class CotizacionActualEstrategiaKey implements Serializable {
    private UUID jugadorId;
    private String estrategiaId;
    private int estrategiaVersion;
    public CotizacionActualEstrategiaKey() { }
    @Override public boolean equals(Object o) { if (!(o instanceof CotizacionActualEstrategiaKey k)) return false; return estrategiaVersion == k.estrategiaVersion && java.util.Objects.equals(jugadorId,k.jugadorId) && java.util.Objects.equals(estrategiaId,k.estrategiaId); }
    @Override public int hashCode() { return java.util.Objects.hash(jugadorId, estrategiaId, estrategiaVersion); }
}
