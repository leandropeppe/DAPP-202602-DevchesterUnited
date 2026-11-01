package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2PLockOrder;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketP2PLockOrderTest {
    @Test
    void ordenaIdentificadoresDeFormaDeterminista() {
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(MarketP2PLockOrder.compareIds(lower, higher)).isNegative();
        assertThat(MarketP2PLockOrder.compareIds(higher, lower)).isPositive();
    }

    @Test
    void rechazaIdentificadoresAusentes() {
        UUID otherId = UUID.randomUUID();
        assertThatThrownBy(() -> MarketP2PLockOrder.compareIds(null, otherId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
