package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

/** Parámetros HTTP del historial de transacciones. */
public final class MarketTransactionQueryParams {
    @Parameter(name = "playerId", in = ParameterIn.QUERY,
            description = "Identificador UUID del jugador.",
            schema = @Schema(type = "string", format = "uuid"))
    private String playerId;

    @Parameter(name = "type", in = ParameterIn.QUERY,
            description = "Tipo: PRIMARY_PURCHASE, SALE legacy, P2P_PURCHASE o P2P_SALE. "
                    + "PURCHASE se conserva como alias compatible de PRIMARY_PURCHASE.",
            schema = @Schema(type = "string", allowableValues = {"PRIMARY_PURCHASE", "PURCHASE",
                    "SALE", "P2P_PURCHASE", "P2P_SALE"}))
    private String type;

    @Parameter(name = "from", in = ParameterIn.QUERY,
            description = "Instante ISO-8601 inclusivo.",
            schema = @Schema(type = "string", format = "date-time"))
    private String from;

    @Parameter(name = "to", in = ParameterIn.QUERY,
            description = "Instante ISO-8601 exclusivo.",
            schema = @Schema(type = "string", format = "date-time"))
    private String to;

    @Parameter(name = "page", in = ParameterIn.QUERY,
            description = "Pagina desde 1.",
            schema = @Schema(type = "integer", minimum = "1", defaultValue = "1"))
    private String page = "1";

    @Parameter(name = "limit", in = ParameterIn.QUERY,
            description = "Registros por pagina, entre 1 y 100.",
            schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
    private String limit = "20";

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page == null ? "1" : page;
    }

    public String getLimit() {
        return limit;
    }

    public void setLimit(String limit) {
        this.limit = limit == null ? "20" : limit;
    }
}
