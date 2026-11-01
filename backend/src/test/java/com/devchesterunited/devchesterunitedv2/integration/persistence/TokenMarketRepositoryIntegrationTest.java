package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AsignacionCreditosInicialesEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.MovimientoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.PnLRealizadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VigenciaCotizacionMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AsignacionCreditosInicialesRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EmisionTokensMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MovimientoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.PnLRealizadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TokenMarketRepositoryIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private EstadoMercadoRepository estados;
    @Autowired
    private JugadorRepository jugadores;
    @Autowired
    private ContraparteMercadoRepository contrapartes;
    @Autowired
    private CuentaCreditosMercadoRepository cuentas;
    @Autowired
    private AsignacionCreditosInicialesRepository asignaciones;
    @Autowired
    private EmisionTokensMercadoRepository emisiones;
    @Autowired
    private TenenciaMercadoRepository tenencias;
    @Autowired
    private VersionCotizacionMercadoRepository cotizaciones;
    @Autowired
    private CabeceraCotizacionMercadoRepository cabeceras;
    @Autowired
    private SolicitudIdempotenciaMercadoRepository solicitudes;
    @Autowired
    private TransaccionConfirmadaMercadoRepository transacciones;
    @Autowired
    private MovimientoMercadoRepository movimientos;
    @Autowired
    private PnLRealizadoMercadoRepository pnl;

    @Test
    void creaElEstadoSingletonDeFormaIdempotenteYPermiteBloquearlo() {
        int firstInsert = estados.ensureSingleton(AHORA);
        assertThat(estados.ensureSingleton(AHORA.plusSeconds(1))).isZero();

        assertThat(firstInsert).isIn(0, 1);
        var state = estados.findSingletonForUpdate().orElseThrow();
        assertThat(state.getId()).isEqualTo((short) 1);
        if (firstInsert == 1) {
            assertThat(state.getStatus()).isEqualTo("UNINITIALIZED");
            assertThat(state.getEpoch()).isEqualTo(1L);
        }
    }

    @Test
    void consultaRecursosPorPropietarioYRespetaElOrdenDeMercado() {
        UUID usuarioId = UUID.randomUUID();
        UUID contraparteId = UUID.randomUUID();
        UUID cuentaId = UUID.randomUUID();
        UUID cuentaContraparteId = UUID.randomUUID();
        UUID asignacionId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID solicitudId = UUID.randomUUID();
        UUID transaccionId = UUID.randomUUID();
        UUID movimientoId = UUID.randomUUID();
        UUID equipoId = jugadores.findAll().stream().findFirst().orElseThrow().getEquipoId();
        JugadorEntity jugador = jugadores.saveAndFlush(new JugadorEntity(
                UUID.randomUUID(), "Repository Player", equipoId, "Forward", 9,
                "Argentina", true));

        usuarios.saveAndFlush(new UsuarioEntity(usuarioId, "repo" + usuarioId.toString().substring(0, 8),
                usuarioId + "@example.test", HASH, AHORA, AHORA));
        UUID requestedCounterpartyId = contraparteId;
        ContraparteMercadoEntity contraparte = contrapartes.findByTipoContraparte("SUPERUSER")
                .orElseGet(() -> contrapartes.saveAndFlush(
                        new ContraparteMercadoEntity(requestedCounterpartyId, "SUPERUSER", "Mercado", AHORA)));
        contraparteId = contraparte.getId();
        cuentas.saveAndFlush(new CuentaCreditosMercadoEntity(cuentaId, usuarioId, null,
                new BigDecimal("100.00"), AHORA, AHORA));
        if (cuentas.findByContraparteId(contraparteId).isEmpty()) {
            cuentas.saveAndFlush(new CuentaCreditosMercadoEntity(cuentaContraparteId, null,
                    contraparteId, new BigDecimal("1000000.00"), AHORA, AHORA));
        }
        asignaciones.saveAndFlush(new AsignacionCreditosInicialesEntity(asignacionId, usuarioId,
                cuentaId, "INITIAL_GRANT", new BigDecimal("1000.00"), AHORA));
        emisiones.saveAndFlush(new EmisionTokensMercadoEntity(jugador.getId(), contraparteId,
                100L, 100L, AHORA, AHORA));

        TenenciaMercadoId tenenciaId = new TenenciaMercadoId(usuarioId, jugador.getId());
        tenencias.saveAndFlush(new TenenciaMercadoEntity(tenenciaId, usuarios.findById(usuarioId).orElseThrow(),
                jugador, 2L, new BigDecimal("2.00"), AHORA));

        VersionCotizacionMercadoId versionId = new VersionCotizacionMercadoId(quoteId, 1L);
        cotizaciones.saveAndFlush(new VersionCotizacionMercadoEntity(versionId, jugador.getId(),
                new BigDecimal("1.00"), new VigenciaCotizacionMercado(AHORA, AHORA, null), "VALID", null, AHORA));
        cabeceras.saveAndFlush(new CabeceraCotizacionMercadoEntity(jugador.getId(), quoteId, 1L, AHORA));

        solicitudes.saveAndFlush(SolicitudIdempotenciaMercadoEntity.builder().id(solicitudId)
                .usuarioId(usuarioId).claveIdempotencia("repo-key").tipoOperacion("PURCHASE")
                .hashParametros("repo-hash").status("CONFIRMED").tokenEjecucion(null)
                .referenciaResultado(transaccionId).creadoEn(AHORA).actualizadoEn(AHORA).build());
        transacciones.saveAndFlush(TransaccionConfirmadaMercadoEntity.builder().id(transaccionId)
                .usuarioId(usuarioId).jugadorId(jugador.getId())
                .nombreJugadorSnapshot(jugador.getNombre()).tipoOperacion("PURCHASE")
                .quantity(2L).precioUnitario(new BigDecimal("1.00"))
                .montoTotal(new BigDecimal("2.00")).contraparteId(contraparteId)
                .quoteId(quoteId).versionCotizacion(1L).solicitudIdempotenciaId(solicitudId)
                .pnlRealizado(null).confirmadoEn(AHORA).build());
        movimientos.saveAndFlush(MovimientoMercadoEntity.builder().id(movimientoId)
                .transaccionId(transaccionId).tipoActivo("TOKEN").cuentaCreditosId(null)
                .usuarioTokenId(usuarioId).contraparteTokenId(null).jugadorId(jugador.getId())
                .variacionCantidad(new BigDecimal("2.00000000")).creadoEn(AHORA).build());
        pnl.saveAndFlush(new PnLRealizadoMercadoEntity(usuarioId, BigDecimal.ZERO, AHORA));

        assertThat(asignaciones.findByUsuarioIdAndTipoAsignacion(usuarioId, "INITIAL_GRANT")).isPresent();
        assertThat(asignaciones.findByUsuarioIdAndTipoAsignacionForUpdate(
                usuarioId, "INITIAL_GRANT")).isPresent();
        assertThat(cuentas.findByUsuarioId(usuarioId)).isPresent();
        assertThat(cuentas.findByUsuarioIdForUpdate(usuarioId)).isPresent();
        assertThat(cuentas.findByContraparteIdForUpdate(contraparteId)).isPresent();
        assertThat(cuentas.findByUsuarioIdWithBalanceAtLeast(usuarioId, new BigDecimal("100.00")))
                .isPresent();
        assertThat(cuentas.findByIdWithBalanceAtLeast(cuentaId, new BigDecimal("100.00"))).isPresent();
        assertThat(cuentas.findByIdWithBalanceAtLeast(cuentaId, new BigDecimal("100.01"))).isEmpty();
        assertThat(emisiones.findByJugadorIdWithInventoryAtLeast(jugador.getId(), 2L)).isPresent();
        assertThat(tenencias.findByUsuarioIdAndJugadorId(usuarioId, jugador.getId())).isPresent();
        assertThat(tenencias.findByUsuarioIdAndJugadorIdWithQuantityAtLeast(usuarioId, jugador.getId(), 2L))
                .isPresent();
        assertThat(tenencias.findByUsuarioIdAndJugadorIdWithQuantityAtLeast(usuarioId, jugador.getId(), 3L))
                .isEmpty();
        assertThat(tenencias.findAllByUsuarioId(usuarioId)).hasSize(1);
        assertThat(cotizaciones.findExactVigente(jugador.getId(), quoteId, 1L, AHORA)).isPresent();
        assertThat(cabeceras.findById(jugador.getId())).get().extracting(CabeceraCotizacionMercadoEntity::getQuoteId)
                .isEqualTo(quoteId);
        assertThat(solicitudes.findByUsuarioIdAndClaveIdempotencia(usuarioId, "repo-key")).isPresent();
        assertThat(solicitudes.findByIdAndUsuarioId(solicitudId, usuarioId)).isPresent();
        assertThat(transacciones.findBySolicitudIdempotenciaId(solicitudId)).isPresent();
        assertThat(transacciones.findAllByUsuarioIdAndJugadorIdOrderByConfirmadoEnDesc(
                usuarioId, jugador.getId())).hasSize(1);
        assertThat(movimientos.findAllByTransaccionIdOrderByCreadoEnAsc(transaccionId)).hasSize(1);
        assertThat(pnl.findById(usuarioId)).isPresent();
        assertThat(contrapartes.findByTipoContraparte("SUPERUSER")).contains(contraparte);
        assertThat(contrapartes.findByTipoContraparteForUpdate("SUPERUSER"))
                .contains(contraparte);
    }
}
