package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.dto.AsignacionCreditosInicialesEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.MovimientoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.PnLRealizadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.VersionCotizacionMercadoId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TokenMarketRepositoryIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired
    private UsuarioRepository usuarios;
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
    void consultaRecursosPorPropietarioYRespetaElOrdenDeMercado() {
        UUID usuarioId = UUID.randomUUID();
        UUID contraparteId = UUID.randomUUID();
        UUID cuentaId = UUID.randomUUID();
        UUID asignacionId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID solicitudId = UUID.randomUUID();
        UUID transaccionId = UUID.randomUUID();
        UUID movimientoId = UUID.randomUUID();
        JugadorEntity jugador = jugadores.findAll().stream().findFirst().orElseThrow();

        usuarios.saveAndFlush(new UsuarioEntity(usuarioId, "repo" + usuarioId.toString().substring(0, 8),
                usuarioId + "@example.test", HASH, AHORA, AHORA));
        ContraparteMercadoEntity contraparte = contrapartes.saveAndFlush(
                new ContraparteMercadoEntity(contraparteId, "SUPERUSER", "Mercado", AHORA));
        cuentas.saveAndFlush(new CuentaCreditosMercadoEntity(cuentaId, usuarioId, null,
                new BigDecimal("100.00"), AHORA, AHORA));
        asignaciones.saveAndFlush(new AsignacionCreditosInicialesEntity(asignacionId, usuarioId,
                cuentaId, "INITIAL_GRANT", new BigDecimal("1000.00"), AHORA));
        emisiones.saveAndFlush(new EmisionTokensMercadoEntity(jugador.getId(), contraparteId,
                100L, 100L, AHORA, AHORA));

        TenenciaMercadoId tenenciaId = new TenenciaMercadoId(usuarioId, jugador.getId());
        tenencias.saveAndFlush(new TenenciaMercadoEntity(tenenciaId, usuarios.findById(usuarioId).orElseThrow(),
                jugador, 2L, new BigDecimal("2.00"), AHORA));

        VersionCotizacionMercadoId versionId = new VersionCotizacionMercadoId(quoteId, 1L);
        cotizaciones.saveAndFlush(new VersionCotizacionMercadoEntity(versionId, jugador.getId(),
                new BigDecimal("1.00"), AHORA, AHORA, null, "VALID", null, AHORA));
        cabeceras.saveAndFlush(new CabeceraCotizacionMercadoEntity(jugador.getId(), quoteId, 1L, AHORA));

        solicitudes.saveAndFlush(new SolicitudIdempotenciaMercadoEntity(solicitudId, usuarioId,
                "repo-key", "PURCHASE", "repo-hash", "CONFIRMED", null, transaccionId, AHORA, AHORA));
        transacciones.saveAndFlush(new TransaccionConfirmadaMercadoEntity(transaccionId, usuarioId,
                jugador.getId(), jugador.getNombre(), "PURCHASE", 2L, new BigDecimal("1.00"),
                new BigDecimal("2.00"), contraparteId, quoteId, 1L, solicitudId, null, AHORA));
        movimientos.saveAndFlush(new MovimientoMercadoEntity(movimientoId, transaccionId, "TOKEN",
                null, usuarioId, null, jugador.getId(), new BigDecimal("2.00000000"), AHORA));
        pnl.saveAndFlush(new PnLRealizadoMercadoEntity(usuarioId, BigDecimal.ZERO, AHORA));

        assertThat(asignaciones.findByUsuarioIdAndTipoAsignacion(usuarioId, "INITIAL_GRANT")).isPresent();
        assertThat(cuentas.findByUsuarioId(usuarioId)).isPresent();
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
    }
}
