package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketP2PQuantityInvariantTest {
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void conservaLaEcuacionDeCantidadesEnOfertaParcial() {
        var offer = new OfertaVentaP2PEntity(UUID.randomUUID(), USER, PLAYER,
                new OfertaVentaP2PEntity.Cantidades(10L, 4L, 6L, 0L),
                OfertaVentaP2PEntity.PARTIALLY_FILLED,
                new OfertaVentaP2PEntity.Auditoria(REQUEST, NOW, NOW));

        assertThat(offer.getCantidadOriginal()).isEqualTo(
                offer.getCantidadRestante() + offer.getCantidadVendida()
                        + offer.getCantidadLiberada());
    }

    @Test
    void rechazaCantidadesQueNoConservanLaInvariante() {
        assertThatThrownBy(() -> new OfertaVentaP2PEntity(UUID.randomUUID(), USER, PLAYER,
                new OfertaVentaP2PEntity.Cantidades(10L, 5L, 4L, 0L),
                OfertaVentaP2PEntity.PARTIALLY_FILLED,
                new OfertaVentaP2PEntity.Auditoria(REQUEST, NOW, NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculaDisponibilidadComoTotalMenosReserva() {
        var holding = new TenenciaMercadoEntity(
                new com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoId(
                        USER, PLAYER), null, null,
                new TenenciaMercadoEntity.Estado(12L, 5L, BigDecimal.ZERO, NOW));

        assertThat(holding.getCantidadDisponible()).isEqualTo(7L);
    }

    @Test
    void rechazaReservaSuperiorALaTenencia() {
        assertThatThrownBy(() -> new TenenciaMercadoEntity(
                new com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoId(
                        USER, PLAYER), null, null,
                new TenenciaMercadoEntity.Estado(4L, 5L, BigDecimal.ZERO, NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
