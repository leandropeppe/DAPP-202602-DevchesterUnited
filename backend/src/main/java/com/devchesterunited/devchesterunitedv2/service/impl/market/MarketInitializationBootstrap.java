package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CuentaCreditosMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.InicializacionMercadoResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bootstrap idempotente de los recursos internos del mercado. */
@Service
public class MarketInitializationBootstrap {
    static final String TIPO_SUPERUSER = "SUPERUSER";
    static final BigDecimal RESERVA_POR_DEFECTO = new BigDecimal("1000000.00");
    private static final BigDecimal COTIZACION_INICIAL = new BigDecimal("1.00");
    private static final String NOMBRE_SUPERUSER = "Token Market Superuser";

    private final EstadoMercadoRepository estados;
    private final ContraparteMercadoRepository contrapartes;
    private final CuentaCreditosMercadoRepository cuentas;
    private final Clock clock;
    private final BigDecimal reservaConfigurada;
    private final MarketTokenIssuanceService emisionesIniciales;
    private final QuotePublicationPort cotizacionesIniciales;

    @Autowired
    public MarketInitializationBootstrap(
            EstadoMercadoRepository estados,
            ContraparteMercadoRepository contrapartes,
            CuentaCreditosMercadoRepository cuentas,
            Clock clock,
            @Value("${token-market.initial-reserve:1000000.00}") BigDecimal reservaConfigurada,
            MarketTokenIssuanceService emisionesIniciales,
            QuotePublicationPort cotizacionesIniciales) {
        this.estados = estados;
        this.contrapartes = contrapartes;
        this.cuentas = cuentas;
        this.clock = clock;
        this.reservaConfigurada = reservaConfigurada;
        this.emisionesIniciales = emisionesIniciales;
        this.cotizacionesIniciales = cotizacionesIniciales;
    }

    /** Constructor conservado para las pruebas del bootstrap fundacional de T019. */
    public MarketInitializationBootstrap(
            EstadoMercadoRepository estados,
            ContraparteMercadoRepository contrapartes,
            CuentaCreditosMercadoRepository cuentas,
            Clock clock,
            BigDecimal reservaConfigurada) {
        this(estados, contrapartes, cuentas, clock, reservaConfigurada, null, null);
    }

    /** Compatibilidad para consumidores que sólo incorporan T022. */
    public MarketInitializationBootstrap(
            EstadoMercadoRepository estados,
            ContraparteMercadoRepository contrapartes,
            CuentaCreditosMercadoRepository cuentas,
            Clock clock,
            BigDecimal reservaConfigurada,
            MarketTokenIssuanceService emisionesIniciales) {
        this(estados, contrapartes, cuentas, clock, reservaConfigurada,
                emisionesIniciales, null);
    }

    @Transactional
    public InicializacionMercadoResultado bootstrap() {
        Instant momento = clock.instant();

        estados.ensureSingleton(momento);
        EstadoMercadoEntity estado = estados.findSingletonForUpdate()
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la fila singleton del estado del mercado."));

        BigDecimal reservaEfectiva = reservaEfectiva(estado);
        validarReserva(reservaEfectiva);

        ContraparteMercadoEntity contraparte = contrapartes.findByTipoContraparteForUpdate(
                        TIPO_SUPERUSER)
                .orElseGet(() -> contrapartes.save(new ContraparteMercadoEntity(
                        UUID.randomUUID(), TIPO_SUPERUSER, NOMBRE_SUPERUSER, momento)));

        var cuenta = cuentas.findByContraparteIdForUpdate(contraparte.getId());
        if (cuenta.isPresent()) {
            if (estado.getReservaInicialInicializada() == null) {
                throw new IllegalStateException(
                        "La cuenta SUPERUSER existe sin reserva inicializada.");
            }
        } else {
            if (estado.getReservaInicialInicializada() == null) {
                estado.inicializarReserva(reservaEfectiva, momento);
            }
            cuentas.save(new CuentaCreditosMercadoEntity(
                    UUID.randomUUID(), null, contraparte.getId(),
                    reservaEfectiva, momento, momento));
        }

        if (emisionesIniciales != null) {
            List<UUID> jugadoresElegibles = emisionesIniciales
                    .emitirJugadoresActivosYObtener(contraparte.getId());
            if (cotizacionesIniciales != null) {
                for (UUID jugadorId : jugadoresElegibles) {
                    cotizacionesIniciales.publishInitialQuote(
                            jugadorId, COTIZACION_INICIAL, true);
                }
            }
        }

        return new InicializacionMercadoResultado(
                EstadoMercado.valueOf(estado.getStatus()),
                estado.getEpoch(),
                estado.getReservaInicialInicializada());
    }

    private BigDecimal reservaEfectiva(EstadoMercadoEntity estado) {
        if (estado.getReservaInicialInicializada() != null) {
            return estado.getReservaInicialInicializada();
        }
        if (estado.getReservaInicialConfigurada() != null) {
            return estado.getReservaInicialConfigurada();
        }
        return reservaConfigurada;
    }

    private static void validarReserva(BigDecimal reserva) {
        if (reserva == null || reserva.signum() < 0 || reserva.scale() > 2
                || reserva.precision() - reserva.scale() > 18) {
            throw new IllegalArgumentException(
                    "La reserva inicial debe ser no negativa y tener como máximo dos decimales.");
        }
    }
}
