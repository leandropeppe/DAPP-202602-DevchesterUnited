package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record MarketBalanceResponseDTO(
        @JsonProperty("balance") BigDecimal saldo) { }
