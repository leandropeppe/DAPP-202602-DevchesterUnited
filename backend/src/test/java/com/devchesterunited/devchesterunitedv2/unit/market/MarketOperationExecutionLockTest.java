package com.devchesterunited.devchesterunitedv2.unit.market;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketOperationExecutionLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MarketOperationExecutionLockTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void usaAdvisoryLockTransaccionalYClaveEstablePorUsuarioYClave() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(true);
        var lock = new MarketOperationExecutionLock(jdbc);

        assertThat(lock.tryAcquire(USER_ID, " market-key ")).isTrue();
        assertThat(lock.tryAcquire(USER_ID, "market-key")).isTrue();

        ArgumentCaptor<Long> keys = ArgumentCaptor.forClass(Long.class);
        verify(jdbc, times(2)).queryForObject(
                org.mockito.ArgumentMatchers.contains("pg_try_advisory_xact_lock"),
                eq(Boolean.class), keys.capture());
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
    }

    @Test
    void devuelveFalseSinLiberarManualmenteCuandoOtroEjecutorPoseeElLock() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(false);
        var lock = new MarketOperationExecutionLock(jdbc);

        assertThat(lock.tryAcquire(USER_ID, "market-key")).isFalse();
        verify(jdbc).queryForObject(
                org.mockito.ArgumentMatchers.contains("pg_try_advisory_xact_lock"),
                eq(Boolean.class), any());
    }

    @Test
    void adquiereElLockDeFormaBloqueanteEnLaTransaccionActual() {
        var lock = new MarketOperationExecutionLock(jdbc);

        lock.acquire(USER_ID, "market-key");

        verify(jdbc).execute(
                org.mockito.ArgumentMatchers.contains("pg_advisory_xact_lock"),
                org.mockito.ArgumentMatchers.<PreparedStatementCallback<Void>>any());
    }
}
