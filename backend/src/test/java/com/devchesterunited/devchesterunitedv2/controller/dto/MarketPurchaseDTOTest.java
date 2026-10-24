package com.devchesterunited.devchesterunitedv2.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MarketPurchaseDTOTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID QUOTE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant UPDATED = Instant.parse("2026-10-15T12:00:00Z");

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void serializaLaSolicitudConNombresPublicosYSinIdentidadNiPrecio() throws Exception {
        MarketPurchaseRequestDTO request = validRequest();

        var json = mapper.readTree(mapper.writeValueAsString(request));

        var fields = new ArrayList<String>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields)
                .containsExactlyInAnyOrder("player_id", "quantity", "quote_id", "quote_version");
        assertThat(json.has("user_id")).isFalse();
        assertThat(json.has("price_per_token")).isFalse();
        assertThat(json.has("total_amount")).isFalse();
        assertThat(json.get("quantity").asLong()).isEqualTo(3L);

        MarketPurchaseRequestDTO decoded = mapper.readValue(
                mapper.writeValueAsString(request), MarketPurchaseRequestDTO.class);
        assertThat(decoded).isEqualTo(request);
    }

    @Test
    void convierteAlComandoInternoConIdentidadYClaveDeCabecera() {
        MarketPurchaseRequestDTO request = validRequest();

        MarketPurchaseRequest command = request.toDomain(USER_ID, "purchase-key");

        assertThat(command.usuarioId()).isEqualTo(USER_ID);
        assertThat(command.claveIdempotencia()).isEqualTo("purchase-key");
        assertThat(command.jugadorId()).isEqualTo(PLAYER_ID);
        assertThat(command.cantidad()).isEqualTo(3L);
        assertThat(command.cotizacionId()).isEqualTo(QUOTE_ID);
        assertThat(command.versionCotizacion()).isEqualTo(2L);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rechazaCamposObligatoriosYLimitesDeCantidad(MarketPurchaseRequestDTO request,
            String field) {
        Set<ConstraintViolation<MarketPurchaseRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(violation ->
                violation.getPropertyPath().toString().equals(field));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new MarketPurchaseRequestDTO(null, 1L, QUOTE_ID, 1L), "jugadorId"),
                Arguments.of(new MarketPurchaseRequestDTO(PLAYER_ID, null, QUOTE_ID, 1L), "cantidad"),
                Arguments.of(new MarketPurchaseRequestDTO(PLAYER_ID, 0L, QUOTE_ID, 1L), "cantidad"),
                Arguments.of(new MarketPurchaseRequestDTO(PLAYER_ID, -1L, QUOTE_ID, 1L), "cantidad"),
                Arguments.of(new MarketPurchaseRequestDTO(PLAYER_ID, 101L, QUOTE_ID, 1L), "cantidad"),
                Arguments.of(new MarketPurchaseRequestDTO(PLAYER_ID, 1L, null, 1L), "cotizacionId"),
                Arguments.of(new MarketPurchaseRequestDTO(PLAYER_ID, 1L, QUOTE_ID, null), "versionCotizacion"),
                Arguments.of(new MarketPurchaseRequestDTO(PLAYER_ID, 1L, QUOTE_ID, 0L), "versionCotizacion"));
    }

    @Test
    void serializaLaRespuestaSinCamposInternosYConImportesDelResultado() throws Exception {
        MarketPurchaseResult result = new MarketPurchaseResult(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                USER_ID, PLAYER_ID, 3L, new BigDecimal("1.25"),
                new BigDecimal("3.75"), MarketIdempotencyStatus.CONFIRMED, UPDATED);

        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPurchaseResponseDTO.fromDomain(result)));

        assertThat(json.get("operation_id").asText()).isEqualTo(result.solicitudId().toString());
        assertThat(json.get("transaction_id").asText()).isEqualTo(result.transaccionId().toString());
        assertThat(json.get("player_id").asText()).isEqualTo(PLAYER_ID.toString());
        assertThat(json.get("quantity").asLong()).isEqualTo(3L);
        assertThat(json.get("unit_price").asText()).isEqualTo("1.25");
        assertThat(json.get("total_amount").asText()).isEqualTo("3.75");
        assertThat(json.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(json.has("user_id")).isFalse();
        assertThat(json.has("execution_token")).isFalse();
        assertThat(json.has("parameters_hash")).isFalse();
    }

    @Test
    void respuestaProcessingNoPresentaTransaccionNiImportesInexistentes() throws Exception {
        MarketPurchaseResult result = new MarketPurchaseResult(
                null, null, USER_ID, PLAYER_ID, null, null, null,
                MarketIdempotencyStatus.PROCESSING, UPDATED);

        var json = mapper.readTree(mapper.writeValueAsString(
                MarketPurchaseResponseDTO.fromDomain(result)));

        assertThat(json.get("status").asText()).isEqualTo("PROCESSING");
        assertThat(json.has("transaction_id")).isFalse();
        assertThat(json.has("total_amount")).isFalse();
    }

    private static MarketPurchaseRequestDTO validRequest() {
        return new MarketPurchaseRequestDTO(PLAYER_ID, 3L, QUOTE_ID, 2L);
    }
}
