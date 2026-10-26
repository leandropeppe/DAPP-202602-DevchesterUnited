package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import java.util.UUID;

/** Guard interno que conserva la propiedad de ejecución durante el núcleo transaccional. */
public interface MarketIdempotencyExecutionPort {
    void asegurarPropiedadDeEjecucion(UUID solicitudId, UUID tokenEjecucion);
}
