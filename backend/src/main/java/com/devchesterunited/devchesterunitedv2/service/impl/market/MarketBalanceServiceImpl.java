package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CuentaCreditosMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketBalanceResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketBalanceServiceImpl implements MarketBalanceService {
    private final EstadoMercadoRepository estados;
    private final CuentaCreditosMercadoRepository cuentas;

    public MarketBalanceServiceImpl(EstadoMercadoRepository estados,
            CuentaCreditosMercadoRepository cuentas) {
        this.estados = estados;
        this.cuentas = cuentas;
    }

    @Override
    // PostgreSQL no permite SELECT ... FOR SHARE dentro de una transaccion
    // marcada como read-only a nivel de conexion. La consulta sigue siendo
    // de solo lectura; se conserva la transaccion normal para poder adquirir
    // el lock compartido definido por el repositorio.
    @Transactional
    public MarketBalanceResult consultar(UUID usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("El identificador del usuario es obligatorio.");
        }
        var estado = estados.findSingletonForRead().orElseThrow(() ->
                new IllegalStateException("No existe la fila singleton del estado del mercado."));
        EstadoMercado estadoMercado = EstadoMercado.valueOf(estado.getStatus());
        return new MarketBalanceResult(
                estadoMercado,
                estado.getEpoch(),
                estadoMercado == EstadoMercado.READY,
                cuentas.findByUsuarioId(usuarioId).map(cuenta -> cuenta.getBalance()));
    }
}
