package com.devchesterunited.devchesterunitedv2.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.controller.dto.MarketPurchaseRequestDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.MarketPurchaseResponseDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketBalanceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketPortfolioService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MarketPurchaseControllerTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID QUOTE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID REQUEST_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID TRANSACTION_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock private MarketBalanceService balances;
    @Mock private MarketAvailabilityService availability;
    @Mock private MarketIdempotencyService idempotency;
    @Mock private MarketPurchaseService purchases;
    @Mock private MarketSaleService sales;
    @Mock private MarketPortfolioService portfolios;

    private MarketController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketController(balances, availability, idempotency, purchases, sales, portfolios);
    }

    @Test
    void derivaUsuarioDelJwtYEjecutaUnaCompraConLaClaveNormalizada() {
        MarketPurchaseRequestDTO request = new MarketPurchaseRequestDTO(PLAYER_ID, 3L, QUOTE_ID, 2L);
        MarketPurchaseResult result = new MarketPurchaseResult(REQUEST_ID, TRANSACTION_ID,
                USER_ID, PLAYER_ID, 3L, new BigDecimal("1.25"), new BigDecimal("3.75"),
                MarketIdempotencyStatus.CONFIRMED, Instant.parse("2026-10-15T12:00:00Z"));
        when(purchases.comprar(new MarketPurchaseRequest(USER_ID, "purchase-key", PLAYER_ID,
                3L, QUOTE_ID, 2L))).thenReturn(result);

        ResponseEntity<?> response = controller.purchase(jwt(USER_ID), " purchase-key ", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        var body = (com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO<MarketPurchaseResponseDTO>) response.getBody();
        assertThat(body.data().estado()).isEqualTo("CONFIRMED");
        assertThat(body.data().montoTotal()).isEqualByComparingTo("3.75");
        verify(purchases).comprar(new MarketPurchaseRequest(USER_ID, "purchase-key", PLAYER_ID,
                3L, QUOTE_ID, 2L));
    }

    @Test
    void claveAusenteVaciaODemasiadoLargaEsEntradaInvalidaSinEjecutarCompra() {
        MarketPurchaseRequestDTO request = new MarketPurchaseRequestDTO(PLAYER_ID, 1L, QUOTE_ID, 1L);

        assertThatThrownBy(() -> controller.purchase(jwt(USER_ID), null, request))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(TokenMarketException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> controller.purchase(jwt(USER_ID), " ", request))
                .isInstanceOf(TokenMarketException.class);
        assertThatThrownBy(() -> controller.purchase(jwt(USER_ID), "x".repeat(201), request))
                .isInstanceOf(TokenMarketException.class);
        verifyNoInteractions(purchases);
    }

    @Test
    void jwtConSujetoMalFormadoSeRechazaAntesDeLaCompra() {
        MarketPurchaseRequestDTO request = new MarketPurchaseRequestDTO(PLAYER_ID, 1L, QUOTE_ID, 1L);

        assertThatThrownBy(() -> controller.purchase(jwtWithMalformedSubject(), "purchase-key", request))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(purchases);
    }

    @Test
    void jwtSinSujetoSeRechazaAntesDeLaCompra() {
        MarketPurchaseRequestDTO request = new MarketPurchaseRequestDTO(PLAYER_ID, 1L, QUOTE_ID, 1L);

        assertThatThrownBy(() -> controller.purchase(jwtWithoutSubject(), "purchase-key", request))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(purchases);
    }

    private static Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("usr_" + userId).build();
    }

    private static Jwt jwtWithMalformedSubject() {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("usuario-invalido").build();
    }

    private static Jwt jwtWithoutSubject() {
        return Jwt.withTokenValue("token").header("alg", "none")
                .claim("iss", "test-issuer").build();
    }
}
