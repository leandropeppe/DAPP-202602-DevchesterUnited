package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketTransactionResult;
import java.util.UUID;

/** Consulta de solo lectura del historial confirmado del usuario autenticado. */
public interface MarketTransactionService {
    MarketTransactionResult consultar(UUID usuarioId, MarketTransactionFilter filtro);
}
