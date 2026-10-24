package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EmisionTokensMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EmisionTokensMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketTokenIssuanceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketTokenIssuanceServiceTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID COUNTERPARTY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock JugadorRepository jugadores;
    @Mock EmisionTokensMercadoRepository emisiones;

    private MarketTokenIssuanceService service;

    @BeforeEach
    void setUp() {
        service = new MarketTokenIssuanceService(jugadores, emisiones,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void emiteExactamenteCienTokensYLosDejaEnLaReserva() {
        JugadorEntity player = player(true);
        when(jugadores.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player));
        when(emisiones.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.empty());

        service.emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID);

        ArgumentCaptor<EmisionTokensMercadoEntity> captor =
                ArgumentCaptor.forClass(EmisionTokensMercadoEntity.class);
        verify(emisiones).save(captor.capture());
        EmisionTokensMercadoEntity issuance = captor.getValue();
        assertThat(issuance.getCantidadEmitida()).isEqualTo(100L);
        assertThat(issuance.getCantidadInventario()).isEqualTo(100L);
        assertThat(issuance.getContraparteId()).isEqualTo(COUNTERPARTY_ID);
    }

    @Test
    void reintentoYReactivacionNoCreanUnaSegundaEmision() {
        JugadorEntity player = player(true);
        EmisionTokensMercadoEntity existing = new EmisionTokensMercadoEntity(
                PLAYER_ID, COUNTERPARTY_ID, 100L, 73L, NOW, NOW);
        when(jugadores.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player));
        when(emisiones.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(existing));

        service.emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID);

        verify(emisiones, never()).save(any());
    }

    @Test
    void jugadorInactivoNoEsElegible() {
        when(jugadores.findAllByActivoTrueOrderByIdAsc()).thenReturn(List.of());

        service.emitirJugadoresActivos(COUNTERPARTY_ID);

        verify(emisiones, never()).findByJugadorIdForUpdate(any());
        verify(emisiones, never()).save(any());
    }

    @Test
    void primeraActivacionNoEmiteMientrasElJugadorSigaInactivo() {
        when(jugadores.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player(false)));

        service.emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID);

        verify(emisiones, never()).findByJugadorIdForUpdate(any());
        verify(emisiones, never()).save(any());
    }

    @Test
    void emisionExistenteInconsistenteDetieneLaInicializacion() {
        when(jugadores.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player(true)));
        when(emisiones.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(
                new EmisionTokensMercadoEntity(PLAYER_ID, COUNTERPARTY_ID,
                        99L, 99L, NOW, NOW)));

        assertThatThrownBy(() -> service.emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(emisiones, never()).save(any());
    }

    private static JugadorEntity player(boolean active) {
        return new JugadorEntity(PLAYER_ID, "T022 Player", UUID.randomUUID(),
                "Forward", 9, "Argentina", active);
    }
}
