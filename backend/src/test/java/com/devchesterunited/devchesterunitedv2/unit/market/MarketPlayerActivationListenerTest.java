package com.devchesterunited.devchesterunitedv2.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.event.JugadorActivadoEvent;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketPlayerActivationListener;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketTokenIssuanceService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketPlayerActivationListenerTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID COUNTERPARTY_ID = UUID.randomUUID();

    @Mock MarketTokenIssuanceService emisiones;
    @Mock ContraparteMercadoRepository contrapartes;

    @Test
    void emiteSoloDespuesDeResolverLaSuperuser() {
        when(contrapartes.findByTipoContraparte("SUPERUSER"))
                .thenReturn(Optional.of(new ContraparteMercadoEntity(
                        COUNTERPARTY_ID, "SUPERUSER", "Superuser", Instant.now())));

        new MarketPlayerActivationListener(emisiones, contrapartes)
                .emitirTrasConfirmacion(new JugadorActivadoEvent(PLAYER_ID));

        verify(emisiones).emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID);
    }

    @Test
    void difiereLaEmisionSiTodaviaNoExisteSuperuser() {
        when(contrapartes.findByTipoContraparte("SUPERUSER"))
                .thenReturn(Optional.empty());

        new MarketPlayerActivationListener(emisiones, contrapartes)
                .emitirTrasConfirmacion(new JugadorActivadoEvent(PLAYER_ID));

        verify(emisiones, never()).emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID);
    }

    @Test
    void unFalloDeMercadoNoSePropagaAlFlujoYaConfirmado() {
        when(contrapartes.findByTipoContraparte("SUPERUSER"))
                .thenReturn(Optional.of(new ContraparteMercadoEntity(
                        COUNTERPARTY_ID, "SUPERUSER", "Superuser", Instant.now())));
        org.mockito.Mockito.doThrow(new IllegalStateException("fallo de mercado"))
                .when(emisiones).emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID);

        new MarketPlayerActivationListener(emisiones, contrapartes)
                .emitirTrasConfirmacion(new JugadorActivadoEvent(PLAYER_ID));

        verify(emisiones).emitirEnPrimeraActivacion(PLAYER_ID, COUNTERPARTY_ID);
    }
}
