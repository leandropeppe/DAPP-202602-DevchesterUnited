package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketPortfolioResult;
import java.util.UUID;

/** Consulta de solo lectura del portfolio del usuario autenticado. */
public interface MarketPortfolioService {
    MarketPortfolioResult consultar(UUID usuarioId);
}
