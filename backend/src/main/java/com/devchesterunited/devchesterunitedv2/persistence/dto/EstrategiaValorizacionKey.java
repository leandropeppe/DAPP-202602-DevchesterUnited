package com.devchesterunited.devchesterunitedv2.persistence.dto;

import java.io.Serializable;

public class EstrategiaValorizacionKey implements Serializable {
    private String id;
    private int version;

    public EstrategiaValorizacionKey() { }
    public EstrategiaValorizacionKey(String id, int version) { this.id = id; this.version = version; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof EstrategiaValorizacionKey key)) return false;
        return version == key.version && java.util.Objects.equals(id, key.id);
    }
    @Override public int hashCode() { return java.util.Objects.hash(id, version); }
}
