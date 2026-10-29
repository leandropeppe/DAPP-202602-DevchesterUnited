package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TradeP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TradeP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MarketP2POfferOptimisticLockIntegrationTest extends PostgresTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String HASH = "integration-hash";
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private JugadorRepository jugadores;
    @Autowired
    private TenenciaMercadoRepository tenencias;
    @Autowired
    private SolicitudIdempotenciaMercadoRepository solicitudes;
    @Autowired
    private OfertaVentaP2PRepository ofertas;
    @Autowired
    private TradeP2PRepository trades;
    @Autowired
    private VersionCotizacionMercadoRepository cotizaciones;
    @PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID usuarioId;
    private UUID jugadorId;
    private UUID solicitudId;
    private UUID ofertaId;

    @BeforeEach
    void prepararOferta() {
        usuarioId = UUID.randomUUID();
        solicitudId = UUID.randomUUID();
        ofertaId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            JugadorEntity jugador = jugadores.findAll().stream().findFirst().orElseThrow();
            jugadorId = jugador.getId();
            UsuarioEntity usuario = new UsuarioEntity(usuarioId,
                    "lock" + usuarioId.toString().substring(0, 8),
                    usuarioId + "@example.test", PASSWORD_HASH, NOW, NOW);
            usuarios.saveAndFlush(usuario);
            tenencias.saveAndFlush(new TenenciaMercadoEntity(
                    new TenenciaMercadoId(usuarioId, jugadorId), usuario, jugador,
                    new TenenciaMercadoEntity.Estado(10L, 0L,
                            BigDecimal.ZERO.setScale(8), NOW)));
            solicitudes.saveAndFlush(new SolicitudIdempotenciaMercadoEntity(
                    solicitudId, usuarioId, "offer-" + ofertaId, "P2P_OFFER", HASH,
                    SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING, UUID.randomUUID(),
                    null, NOW, NOW));
            ofertas.saveAndFlush(new OfertaVentaP2PEntity(
                    ofertaId, usuarioId, jugadorId,
                    new OfertaVentaP2PEntity.Cantidades(10L, 10L, 0L, 0L),
                    OfertaVentaP2PEntity.OPEN,
                    new OfertaVentaP2PEntity.Auditoria(solicitudId, NOW, null)));
        });
    }

    @AfterEach
    void limpiarOferta() {
        jdbc.update("ALTER TABLE market_p2p_trades DISABLE TRIGGER trg_market_p2p_trades_immutable");
        try {
            jdbc.update("DELETE FROM market_p2p_trades WHERE offer_id = ?", ofertaId);
        } finally {
            jdbc.update("ALTER TABLE market_p2p_trades ENABLE TRIGGER trg_market_p2p_trades_immutable");
        }
        jdbc.update("DELETE FROM market_p2p_offers WHERE id = ?", ofertaId);
        jdbc.update("DELETE FROM market_idempotency_requests WHERE id = ?", solicitudId);
        jdbc.update("DELETE FROM market_holdings WHERE user_id = ? AND player_id = ?",
                usuarioId, jugadorId);
        jdbc.update("DELETE FROM usuarios WHERE id = ?", usuarioId);
    }

    @Test
    void detectaLaSegundaModificacionConcurrenteDeLaOferta() throws Exception {
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = executor.submit(() -> actualizarOferta(9L, 1L, bothLoaded));
            Future<Throwable> second = executor.submit(() -> actualizarOferta(8L, 2L, bothLoaded));

            Throwable firstFailure = first.get(30, TimeUnit.SECONDS);
            Throwable secondFailure = second.get(30, TimeUnit.SECONDS);

            List<Throwable> failures = Arrays.asList(firstFailure, secondFailure);
            assertThat(failures).anyMatch(failure -> failure == null);
            assertThat(failures)
                    .anyMatch(failure -> failure instanceof ObjectOptimisticLockingFailureException);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional
    void resuelveLaTenenciaCompradoraDesdeElTrade() {
        UUID compradorId = UUID.randomUUID();
        UUID solicitudCompraId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        JugadorEntity jugador = jugadores.findById(jugadorId).orElseThrow();
        UsuarioEntity comprador = new UsuarioEntity(compradorId,
                "buyer" + compradorId.toString().substring(0, 8),
                compradorId + "@example.test", PASSWORD_HASH, NOW, NOW);
        usuarios.saveAndFlush(comprador);
        tenencias.saveAndFlush(new TenenciaMercadoEntity(
                new TenenciaMercadoId(compradorId, jugadorId), comprador, jugador,
                new TenenciaMercadoEntity.Estado(0L, 0L,
                        BigDecimal.ZERO.setScale(8), NOW)));
        cotizaciones.saveAndFlush(new VersionCotizacionMercadoEntity(
                new VersionCotizacionMercadoId(quoteId, 1L), jugadorId,
                BigDecimal.ONE, NOW, NOW, null, "VALID", null, NOW));
        solicitudes.saveAndFlush(new SolicitudIdempotenciaMercadoEntity(
                solicitudCompraId, compradorId, "purchase-" + ofertaId, "P2P_PURCHASE",
                HASH, SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING,
                UUID.randomUUID(), null, NOW, NOW));
        UUID tradeId = UUID.randomUUID();
        trades.saveAndFlush(new TradeP2PEntity(
                new TradeP2PEntity.Identidad(tradeId, ofertaId, usuarioId, compradorId, jugadorId),
                new TradeP2PEntity.Liquidacion(jugador.getNombre(), 1L,
                        BigDecimal.ONE, BigDecimal.ONE),
                new TradeP2PEntity.Confirmacion(quoteId, 1L, solicitudCompraId, NOW)));
        entityManager.clear();

        TradeP2PEntity persisted = trades.findById(tradeId).orElseThrow();

        assertThat(persisted.getTenenciaComprador().getId())
                .isEqualTo(new TenenciaMercadoId(compradorId, jugadorId));
    }

    private Throwable actualizarOferta(Long restante, Long vendida,
            CyclicBarrier bothLoaded) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            transaction.executeWithoutResult(status -> {
                OfertaVentaP2PEntity oferta = ofertas.findById(ofertaId).orElseThrow();
                esperarAmbasTransacciones(bothLoaded);
                oferta.actualizarCantidades(restante, vendida, 0L,
                        OfertaVentaP2PEntity.PARTIALLY_FILLED, NOW.plusSeconds(vendida));
                ofertas.saveAndFlush(oferta);
            });
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void esperarAmbasTransacciones(CyclicBarrier bothLoaded) {
        try {
            bothLoaded.await(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Las transacciones no alcanzaron la barrera.", failure);
        }
    }
}
