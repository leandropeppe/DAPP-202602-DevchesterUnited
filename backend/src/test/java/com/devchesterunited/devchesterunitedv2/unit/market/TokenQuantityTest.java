package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.exception.CantidadTokenInvalidaException;
import com.devchesterunited.devchesterunitedv2.modelo.market.TokenQuantity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class TokenQuantityTest {

    @Test
    void acceptsPositiveIntegerQuantities() {
        assertThat(new TokenQuantity(1).value()).isEqualTo(1);
        assertThat(new TokenQuantity(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsNonPositiveQuantities() {
        assertAll(
                () -> assertThatThrownBy(() -> new TokenQuantity(0))
                        .isInstanceOf(CantidadTokenInvalidaException.class)
                        .hasMessageContaining("0"),
                () -> assertThatThrownBy(() -> new TokenQuantity(-1))
                        .isInstanceOf(CantidadTokenInvalidaException.class)
                        .hasMessageContaining("-1"));
    }
}
