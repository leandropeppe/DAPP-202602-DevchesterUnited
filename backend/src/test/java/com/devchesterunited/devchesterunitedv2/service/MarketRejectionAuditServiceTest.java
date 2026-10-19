package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.devchesterunited.devchesterunitedv2.persistence.AuditoriaRechazoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.AuditoriaRechazoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.impl.MarketRejectionAuditServiceImpl;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketRejectionAuditServiceTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant CREATED_AT = Instant.parse("2026-10-19T12:00:00Z");
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private AuditoriaRechazoMercadoRepository auditoria;
    @Mock
    private TransaccionConfirmadaMercadoRepository transacciones;

    private MarketRejectionAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketRejectionAuditServiceImpl(auditoria);
    }

    @Test
    void registraTodosLosCamposPermitidos() {
        MarketRejectedAttempt attempt = new MarketRejectedAttempt(USER_ID, "sale", PLAYER_ID,
                HASH, "insufficient_inventory", HASH, CREATED_AT);

        service.registrar(attempt);

        ArgumentCaptor<AuditoriaRechazoMercadoEntity> captor =
                ArgumentCaptor.forClass(AuditoriaRechazoMercadoEntity.class);
        verify(auditoria).save(captor.capture());
        AuditoriaRechazoMercadoEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsuarioId()).isEqualTo(USER_ID);
        assertThat(saved.getTipoOperacion()).isEqualTo("SALE");
        assertThat(saved.getJugadorId()).isEqualTo(PLAYER_ID);
        assertThat(saved.getHashClaveIdempotencia()).isEqualTo(HASH);
        assertThat(saved.getCodigoMotivo()).isEqualTo("INSUFFICIENT_INVENTORY");
        assertThat(saved.getHashParametros()).isEqualTo(HASH);
        assertThat(saved.getCreadoEn()).isEqualTo(CREATED_AT);
        verifyNoInteractions(transacciones);
    }

    @Test
    void permiteUsuarioYJugadorAusentes() {
        service.registrar(new MarketRejectedAttempt(null, "PURCHASE", null, null,
                "INVALID_INPUT", null, CREATED_AT));

        verify(auditoria).save(any(AuditoriaRechazoMercadoEntity.class));
    }

    @Test
    void rechazaTipoDeOperacionInvalido() {
        assertThatThrownBy(() -> new MarketRejectedAttempt(USER_ID, "TRANSFER", PLAYER_ID,
                null, "INVALID_INPUT", null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    void rechazaMotivoInvalido() {
        assertThatThrownBy(() -> new MarketRejectedAttempt(USER_ID, "PURCHASE", PLAYER_ID,
                null, "UNKNOWN_REASON", null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    void rechazaFechaNula() {
        assertThatThrownBy(() -> new MarketRejectedAttempt(USER_ID, "PURCHASE", PLAYER_ID,
                null, "INVALID_INPUT", null, null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    void rechazaHashesMalformados() {
        assertThatThrownBy(() -> new MarketRejectedAttempt(USER_ID, "PURCHASE", PLAYER_ID,
                "raw-key", "INVALID_INPUT", HASH, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketRejectedAttempt(USER_ID, "PURCHASE", PLAYER_ID,
                HASH, "INVALID_INPUT", "not-a-hash", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria);
    }
}
