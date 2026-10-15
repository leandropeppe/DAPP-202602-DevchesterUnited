package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record MarketBalanceResponseDTO(
        @JsonProperty("balance") BigDecimal saldo) { }
