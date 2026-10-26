package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.dto.market.InicializacionMercadoResultado;
import com.devchesterunited.devchesterunitedv2.service.impl.market.InicializacionCreditosUsuariosService;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationBootstrap;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationCoordinator;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationStateService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketInitializationService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketInitializationServiceTest {
    private static final BigDecimal RESERVA_CONFIGURADA = new BigDecimal("250000.00");

    @Mock MarketInitializationStateService estados;
    @Mock MarketInitializationBootstrap bootstrap;
    @Mock InicializacionCreditosUsuariosService creditos;

    private MarketInitializationService servicio;

    @BeforeEach
    void preparar() {
        var coordinador = new MarketInitializationCoordinator(
                estados, bootstrap, creditos, null);
        servicio = new MarketInitializationServiceImpl(coordinador);
    }

    @Test
    void inicializaMercadoDesdeEstadoNoInicializadoYAlcanzaReady() {
        prepararTransicion(2L, 3L, null);

        var resultado = servicio.initialize();

        assertThat(resultado.estado()).isEqualTo(EstadoMercado.READY);
        assertThat(resultado.epoca()).isEqualTo(3L);
        verify(bootstrap).bootstrap();
    }

    @Test
    void conservaLaReservaInicialConfiguradaComoReservaEfectiva() {
        prepararTransicion(2L, 3L, RESERVA_CONFIGURADA);

        var resultado = servicio.initialize();

        assertThat(resultado.reservaInicialEfectiva())
                .isEqualByComparingTo(RESERVA_CONFIGURADA);
    }

    @Test
    void reintentaDesdeFailedYConservaLaReservaYaInicializada() {
        prepararTransicion(4L, 5L, RESERVA_CONFIGURADA);

        var resultado = servicio.initialize();

        assertThat(resultado.estado()).isEqualTo(EstadoMercado.READY);
        assertThat(resultado.epoca()).isEqualTo(5L);
        assertThat(resultado.reservaInicialEfectiva())
                .isEqualByComparingTo(RESERVA_CONFIGURADA);
    }

    @Test
    void reanudaMercadoDesdeInitializingYCompletaLaEpocaSiguiente() {
        prepararTransicion(2L, 3L, RESERVA_CONFIGURADA);

        var resultado = servicio.initialize();

        assertThat(resultado.estado()).isEqualTo(EstadoMercado.READY);
        assertThat(resultado.epoca()).isEqualTo(3L);
    }

    @Test
    void conservaMercadoReadySinReiniciarEpocaNiReserva() {
        var listo = resultado(EstadoMercado.READY, 7L, RESERVA_CONFIGURADA);
        when(estados.comenzarOReanudar()).thenReturn(listo);

        var resultado = servicio.initialize();

        assertThat(resultado).isSameAs(listo);
        verify(bootstrap, never()).bootstrap();
        verify(creditos, never()).asignarAUsuariosExistentes();
        verify(estados, never()).marcarReady(7L);
    }

    private void prepararTransicion(long epochInicial, long epochReady,
            BigDecimal reserva) {
        when(estados.comenzarOReanudar())
                .thenReturn(resultado(EstadoMercado.INITIALIZING, epochInicial, reserva));
        when(estados.marcarReady(epochInicial))
                .thenReturn(Optional.of(resultado(EstadoMercado.READY, epochReady, reserva)));
    }

    private static InicializacionMercadoResultado resultado(
            EstadoMercado estado, long epoca, BigDecimal reserva) {
        return new InicializacionMercadoResultado(estado, epoca, reserva);
    }
}
