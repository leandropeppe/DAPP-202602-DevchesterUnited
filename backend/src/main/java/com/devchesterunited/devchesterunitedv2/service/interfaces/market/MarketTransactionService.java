package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;

import java.util.UUID;

/** Consulta de solo lectura del historial confirmado del usuario autenticado. */
public interface MarketTransactionService {
    MarketTransactionResult consultar(UUID usuarioId, MarketTransactionFilter filtro);
}
