package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Persiste la reserva y la oferta dentro del mismo límite transaccional. */
@Service
public class MarketP2POfferPersistenceServiceImpl {
    private final TenenciaMercadoRepository tenencias;
    private final OfertaVentaP2PRepository ofertas;

    @PersistenceContext
    private EntityManager entityManager;

    public MarketP2POfferPersistenceServiceImpl(TenenciaMercadoRepository tenencias,
            OfertaVentaP2PRepository ofertas) {
        this.tenencias = tenencias;
        this.ofertas = ofertas;
    }

    @Transactional
    public OfertaVentaP2PEntity persistir(UUID solicitudId, UUID vendedorId, UUID jugadorId,
            Long cantidad, Instant momento) {
        TenenciaMercadoEntity tenencia = tenencias
                .findByUsuarioIdAndJugadorIdForUpdate(vendedorId, jugadorId)
                .orElseThrow(this::disponibilidadInsuficiente);
        if (cantidad > tenencia.getCantidadDisponible()) {
            throw disponibilidadInsuficiente();
        }

        tenencia.reservar(cantidad, momento);
        OfertaVentaP2PEntity oferta = new OfertaVentaP2PEntity(
                UUID.randomUUID(), vendedorId, jugadorId,
                new OfertaVentaP2PEntity.Cantidades(cantidad, cantidad, 0L, 0L),
                OfertaVentaP2PEntity.OPEN,
                new OfertaVentaP2PEntity.Auditoria(solicitudId, momento, momento));
        entityManager.persist(oferta);
        entityManager.flush();

        Long remanente = ofertas.sumRemainingActiveByVendedorIdAndJugadorId(
                vendedorId, jugadorId);
        if (!tenencia.getCantidadReservada().equals(remanente)) {
            throw new IllegalStateException("La reserva de la tenencia no coincide con las ofertas.");
        }
        return oferta;
    }

    private TokenMarketException disponibilidadInsuficiente() {
        return new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_AVAILABLE_QUANTITY,
                "La tenencia no tiene cantidad disponible suficiente.");
    }
}
