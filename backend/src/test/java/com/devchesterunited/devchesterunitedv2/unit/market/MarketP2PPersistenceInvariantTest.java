package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaCancelacionOfertaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketP2PPersistenceInvariantTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void reservaDisponibilidadYConsumoMantienenLaInvarianteContable() {
        TenenciaMercadoEntity tenencia = new TenenciaMercadoEntity(
                null, null, null,
                new TenenciaMercadoEntity.Estado(10L, 0L, BigDecimal.ZERO.setScale(8), NOW));

        tenencia.reservar(4L, NOW.plusSeconds(1));
        assertEquals(4L, tenencia.getCantidadReservada());
        assertEquals(6L, tenencia.getCantidadDisponible());

        tenencia.liberarReserva(1L, NOW.plusSeconds(2));
        assertEquals(3L, tenencia.getCantidadReservada());
        assertEquals(7L, tenencia.getCantidadDisponible());

        tenencia.consumirReserva(2L, NOW.plusSeconds(3));
        assertEquals(8L, tenencia.getQuantity());
        assertEquals(1L, tenencia.getCantidadReservada());
        assertEquals(7L, tenencia.getCantidadDisponible());
    }

    @Test
    void noPermiteReservarPorEncimaDeLaDisponibilidad() {
        TenenciaMercadoEntity tenencia = new TenenciaMercadoEntity(
                null, null, null,
                new TenenciaMercadoEntity.Estado(3L, 2L, BigDecimal.ZERO.setScale(8), NOW));

        Instant reservationTime = NOW.plusSeconds(1);
        assertThrows(IllegalStateException.class,
                () -> tenencia.reservar(2L, reservationTime));
    }

    @Test
    void ofertaYAuditoriaConservanLasInvariantesDeCantidad() {
        UUID offerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID idempotencyId = UUID.randomUUID();

        OfertaVentaP2PEntity oferta = new OfertaVentaP2PEntity(
                offerId, sellerId, playerId,
                new OfertaVentaP2PEntity.Cantidades(10L, 4L, 6L, 0L),
                OfertaVentaP2PEntity.PARTIALLY_FILLED,
                new OfertaVentaP2PEntity.Auditoria(idempotencyId, NOW, NOW));
        AuditoriaCancelacionOfertaP2PEntity auditoria =
                new AuditoriaCancelacionOfertaP2PEntity(
                        new AuditoriaCancelacionOfertaP2PEntity.Identidad(
                                UUID.randomUUID(), offerId, sellerId, UUID.randomUUID()),
                        new AuditoriaCancelacionOfertaP2PEntity.Cantidades(10L, 6L, 4L),
                        NOW.plusSeconds(1));

        assertEquals(10L, oferta.getCantidadVendida() + oferta.getCantidadRestante()
                + oferta.getCantidadLiberada());
        assertEquals(10L, auditoria.getCantidadVendida() + auditoria.getCantidadLiberada());
    }
}
