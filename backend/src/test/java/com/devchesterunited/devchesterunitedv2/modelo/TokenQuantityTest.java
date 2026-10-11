package com.devchesterunited.devchesterunitedv2.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.modelo.exception.CantidadTokenInvalidaException;
import org.junit.jupiter.api.Test;

class TokenQuantityTest {

    @Test
    void acceptsPositiveIntegerQuantities() {
        assertThat(new TokenQuantity(1).value()).isEqualTo(1);
        assertThat(new TokenQuantity(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> new TokenQuantity(0))
                .isInstanceOf(CantidadTokenInvalidaException.class)
                .hasMessageContaining("0");
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> new TokenQuantity(-1))
                .isInstanceOf(CantidadTokenInvalidaException.class)
                .hasMessageContaining("-1");
    }
}
