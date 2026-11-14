package com.devchesterunited.devchesterunitedv2.contract.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.configuration.OpenApiConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityErrorHandler;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketController;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketP2PCancellationController;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketP2POfferController;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketP2POfferQueryController;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketP2PPurchaseController;
import com.devchesterunited.devchesterunitedv2.controller.advice.GlobalExceptionHandler;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferQueryService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPortfolioService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

/** Verifica el contrato OpenAPI generado por Spring para el mercado y P2P. */
class MarketP2PApiContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void documentoOpenApiExponeRutasYRespuestas() throws Exception {
        JsonNode document = openApiDocument();
        assertOperation(document, "/api/v1/market/p2p/offers", "get", Set.of("200", "400", "401"));
        assertOperation(document, "/api/v1/market/p2p/offers", "post", Set.of("200", "400", "401", "409", "503"));
        assertOperation(document, "/api/v1/market/p2p/offers/{offerId}/purchases", "post",
                Set.of("200", "400", "401", "404", "409", "503"));
        assertOperation(document, "/api/v1/market/p2p/offers/{offerId}/cancel", "post",
                Set.of("200", "400", "401", "403", "404", "409"));
        assertOperation(document, "/api/v1/market/portfolio", "get", Set.of("200", "401"));
        assertOperation(document, "/api/v1/market/transactions", "get", Set.of("200", "400", "401"));
        assertOperation(document, "/api/v1/market/purchases", "post",
                Set.of("200", "400", "401", "404", "409", "503"));
        assertOperation(document, "/api/v1/market/sales", "post", Set.of("410"));
        assertOperation(document, "/api/v1/market/operations/{idempotency-key}", "get",
                Set.of("200", "400", "401", "404"));
    }

    @Test
    void documentoOpenApiExponeParametrosYHeaders() throws Exception {
        JsonNode document = openApiDocument();
        JsonNode offers = operation(document, "/api/v1/market/p2p/offers", "get");
        assertQueryParameter(offers, "page", "1");
        assertQueryParameter(offers, "limit", "20");
        assertQueryParameter(offers, "playerId", null);
        assertQueryParameter(offers, "status", null);
        assertQueryParameter(offers, "mine", "false");

        JsonNode transactions = operation(document, "/api/v1/market/transactions", "get");
        assertQueryParameter(transactions, "page", "1");
        assertQueryParameter(transactions, "limit", "20");
        for (String parameter : Set.of("playerId", "type", "from", "to")) {
            assertQueryParameter(transactions, parameter, null);
        }

        assertRequiredHeader(operation(document, "/api/v1/market/p2p/offers", "post"), "Idempotency-Key");
        assertRequiredHeader(operation(document, "/api/v1/market/p2p/offers/{offerId}/purchases", "post"), "Idempotency-Key");
        assertRequiredHeader(operation(document, "/api/v1/market/p2p/offers/{offerId}/cancel", "post"), "Idempotency-Key");
    }

    @Test
    void documentoOpenApiExponeEsquemasYSeguridad() throws Exception {
        JsonNode document = openApiDocument();
        JsonNode purchase = operation(document, "/api/v1/market/p2p/offers/{offerId}/purchases", "post");
        assertThat(purchase.path("responses").path("409").toString())
                .contains("MarketP2PErrorResponseDTO");
        Set<String> errorProperties = new HashSet<>();
        document.path("components").path("schemas")
                .path("MarketP2PErrorResponseDTO").path("properties")
                .fieldNames().forEachRemaining(errorProperties::add);
        assertThat(errorProperties).contains("current_quote", "offer_status", "code", "status");

        assertSchemaProperties(document, "MarketPortfolioPositionResponseDTO",
                "total_quantity", "reserved_quantity", "available_quantity");
        assertSchemaProperties(document, "MarketTransactionResponseDTO",
                "operation_type", "realized_pnl");
        assertSchemaProperties(document, "MarketP2POfferRequestDTO", "player_id", "quantity");
        assertSchemaProperties(document, "MarketP2PPurchaseRequestDTO",
                "quantity", "quote_id", "quote_version");

        assertThat(document.path("components").path("securitySchemes")
                .path("BearerAuth").path("scheme").asText()).isEqualTo("bearer");
        for (String path : Set.of(
                "/api/v1/market/p2p/offers",
                "/api/v1/market/p2p/offers/{offerId}/purchases",
                "/api/v1/market/p2p/offers/{offerId}/cancel",
                "/api/v1/market/portfolio",
                "/api/v1/market/transactions",
                "/api/v1/market/purchases",
                "/api/v1/market/sales",
                "/api/v1/market/operations/{idempotency-key}")) {
            for (String method : methodsFor(document, path)) {
                assertThat(operation(document, path, method).path("security").toString())
                        .contains("BearerAuth");
            }
        }
    }

    private JsonNode openApiDocument() throws Exception {
        try (var context = new SpringApplicationBuilder(ContractApplication.class)
                .properties("spring.profiles.active=test", "springdoc.api-docs.enabled=true")
                .run()) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .apply(springSecurity()).build();
            return mapper.readTree(mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        }
    }

    private static JsonNode operation(JsonNode document, String path, String method) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.isObject()).as("OpenAPI operation %s %s", method, path).isTrue();
        return operation;
    }

    private static void assertOperation(JsonNode document, String path, String method,
            Set<String> expectedResponses) {
        JsonNode operation = operation(document, path, method);
        assertThat(responseCodes(operation)).containsAll(expectedResponses);
        assertThat(operation.path("security").toString()).contains("BearerAuth");
    }

    private static Set<String> responseCodes(JsonNode operation) {
        Set<String> codes = new HashSet<>();
        operation.path("responses").fieldNames().forEachRemaining(codes::add);
        return codes;
    }

    private static void assertRequiredHeader(JsonNode operation, String name) {
        JsonNode parameter = findParameter(operation, name);
        assertThat(parameter.isObject()).as("header %s", name).isTrue();
        assertThat(parameter.path("in").asText()).isEqualTo("header");
        assertThat(parameter.path("required").asBoolean()).isTrue();
    }

    private static void assertQueryParameter(JsonNode operation, String name, String defaultValue) {
        JsonNode parameter = findParameter(operation, name);
        assertThat(parameter.isObject()).as("query parameter %s", name).isTrue();
        assertThat(parameter.path("in").asText()).isEqualTo("query");
        if (defaultValue == null) {
            assertThat(parameter.path("required").asBoolean(false)).isFalse();
        } else {
            assertThat(parameter.path("schema").path("default").asText()).isEqualTo(defaultValue);
        }
    }

    private static JsonNode findParameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return operation.path("missing-parameter");
    }

    private static void assertSchemaProperties(JsonNode document, String schemaName,
            String... properties) {
        JsonNode schema = document.path("components").path("schemas").path(schemaName);
        assertThat(schema.isObject()).as("schema %s", schemaName).isTrue();
        for (String property : properties) {
            assertThat(schema.path("properties").has(property))
                    .as("property %s.%s", schemaName, property).isTrue();
        }
    }

    private static Set<String> methodsFor(JsonNode document, String path) {
        Set<String> methods = new HashSet<>();
        Iterator<String> fields = document.path("paths").path(path).fieldNames();
        fields.forEachRemaining(method -> {
            if (!method.equals("parameters")) {
                methods.add(method);
            }
        });
        return methods;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({MarketController.class, MarketP2POfferController.class,
            MarketP2POfferQueryController.class, MarketP2PPurchaseController.class,
            MarketP2PCancellationController.class, GlobalExceptionHandler.class,
            SecurityConfig.class, SecurityErrorHandler.class, OpenApiConfig.class})
    static class ContractApplication {
        @Bean
        MarketBalanceService balances() { return mock(MarketBalanceService.class); }

        @Bean
        MarketAvailabilityService availability() { return mock(MarketAvailabilityService.class); }

        @Bean
        MarketIdempotencyService idempotency() { return mock(MarketIdempotencyService.class); }

        @Bean
        MarketPurchaseService purchases() { return mock(MarketPurchaseService.class); }

        @Bean
        MarketPortfolioService portfolios() { return mock(MarketPortfolioService.class); }

        @Bean
        MarketTransactionService transactions() { return mock(MarketTransactionService.class); }

        @Bean
        MarketP2POfferService p2pOffers() { return mock(MarketP2POfferService.class); }

        @Bean
        MarketP2POfferQueryService p2pOfferQueries() { return mock(MarketP2POfferQueryService.class); }

        @Bean
        MarketP2PPurchaseService p2pPurchases() { return mock(MarketP2PPurchaseService.class); }

        @Bean
        MarketP2PCancellationService p2pCancellations() { return mock(MarketP2PCancellationService.class); }

        @Bean
        OfertaVentaP2PRepository p2pOfferRepository() { return mock(OfertaVentaP2PRepository.class); }
    }
}
