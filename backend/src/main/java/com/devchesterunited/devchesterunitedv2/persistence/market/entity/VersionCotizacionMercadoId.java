package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class VersionCotizacionMercadoId implements Serializable {
    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(nullable = false)
    private Long version;

    protected VersionCotizacionMercadoId() { }

    public VersionCotizacionMercadoId(UUID quoteId, Long version) {
        this.quoteId = quoteId;
        this.version = version;
    }

    public UUID getQuoteId() { return quoteId; }
    public Long getVersion() { return version; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VersionCotizacionMercadoId that)) return false;
        return Objects.equals(quoteId, that.quoteId)
                && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quoteId, version);
    }
}
