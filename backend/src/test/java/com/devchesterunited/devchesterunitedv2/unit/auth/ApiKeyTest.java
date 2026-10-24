package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;
import com.devchesterunited.devchesterunitedv2.modelo.auth.APIKey;

import com.devchesterunited.devchesterunitedv2.exception.ApiKeyException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Contrato puro de APIKey: no conoce Spring, JPA ni infraestructura. */
class ApiKeyTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    private static final Instant CREADA = Instant.parse("2026-09-10T12:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String PREFIJO = "dev_key_123456";

    @Test
    void construyeActivaConEstadoCoherenteYSinExpiracion() {
        var clave = new APIKey(ID, USUARIO, HASH, PREFIJO, true, CREADA, null);
        assertThat(clave.getId()).isEqualTo(ID);
        assertThat(clave.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(clave.getHashClave()).isEqualTo(HASH);
        assertThat(clave.getPrefijo()).isEqualTo(PREFIJO);
        assertThat(clave.isActiva()).isTrue();
        assertThat(clave.getCreadaEn()).isEqualTo(CREADA);
        assertThat(clave.getRevocadaEn()).isNull();
        assertThat(clave.toString()).doesNotContain("dev_key_");
    }

    @Test
    void revocaUnaSolaVezYLaTransicionEsIrreversible() {
        var clave = new APIKey(ID, USUARIO, HASH, PREFIJO, true, CREADA, null);
        Instant revocada = CREADA.plusSeconds(30);

        clave.revocar(revocada);

        assertThat(clave.isActiva()).isFalse();
        assertThat(clave.getRevocadaEn()).isEqualTo(revocada);

        Instant segundaRevocacion = revocada.plusSeconds(1);

        assertThatThrownBy(() -> clave.revocar(segundaRevocacion))
                .isInstanceOf(ApiKeyException.class);

        assertThatThrownBy(clave::activar)
                .isInstanceOf(ApiKeyException.class);
    }

    @Test
    void noTieneVencimientoAunqueAvanceElReloj() {
        var clave = new APIKey(ID, USUARIO, HASH, PREFIJO, true, CREADA, null);
        Clock futuro = Clock.fixed(CREADA.plusSeconds(365 * 24 * 3600), ZoneOffset.UTC);
        assertThat(clave.estaVigenteEn(futuro.instant())).isTrue();
        clave.revocar(futuro.instant());
        assertThat(clave.estaVigenteEn(futuro.instant().plusSeconds(1))).isFalse();
    }

    @Test
    void rechazaFechasYEstadosIncoherentes() {
        assertThatThrownBy(() ->
                new APIKey(ID, USUARIO, HASH, PREFIJO, false, CREADA, null))
                .isInstanceOf(ApiKeyException.class);

        assertThatThrownBy(() ->
                new APIKey(ID, USUARIO, HASH, PREFIJO, true, CREADA, CREADA))
                .isInstanceOf(ApiKeyException.class);

        Instant revocadaAntesDeCreacion = CREADA.minusSeconds(1);

        assertThatThrownBy(() ->
                new APIKey(ID, USUARIO, HASH, PREFIJO, false, CREADA, revocadaAntesDeCreacion))
                .isInstanceOf(ApiKeyException.class);

        assertThatThrownBy(() ->
                new APIKey(ID, USUARIO, "", PREFIJO, true, CREADA, null))
                .isInstanceOf(ApiKeyException.class);
    }
}
