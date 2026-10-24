package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Vista pública de una solicitud; excluye token de ejecución, hash e identificadores internos. */
public record MarketOperationResponseDTO(
        @JsonProperty("idempotency_key") String claveIdempotencia,
        @JsonProperty("operation_type") String tipoOperacion,
        @JsonProperty("status") String estado,
        @JsonProperty("result_reference") @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID referenciaResultado,
        @JsonProperty("updated_at") Instant actualizadoEn) { }
