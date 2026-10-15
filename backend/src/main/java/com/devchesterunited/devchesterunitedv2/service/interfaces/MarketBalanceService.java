package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketBalanceResult;
import java.util.UUID;

/** Consulta de solo lectura del saldo de créditos del usuario autenticado. */
public interface MarketBalanceService {
    MarketBalanceResult consultar(UUID usuarioId);
}
