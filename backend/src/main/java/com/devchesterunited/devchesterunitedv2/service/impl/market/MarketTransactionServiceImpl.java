package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MarketUnifiedTransactionProjection;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MarketUnifiedTransactionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionRecord;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MarketTransactionServiceImpl implements MarketTransactionService {
    private final TransaccionConfirmadaMercadoRepository transacciones;
    private final MarketUnifiedTransactionRepository historialUnificado;

    @Autowired
    public MarketTransactionServiceImpl(TransaccionConfirmadaMercadoRepository transacciones,
            MarketUnifiedTransactionRepository historialUnificado) {
        this.transacciones = Objects.requireNonNull(transacciones);
        this.historialUnificado = Objects.requireNonNull(historialUnificado);
    }

    /** Constructor de compatibilidad para las pruebas y consumidores legacy. */
    public MarketTransactionServiceImpl(TransaccionConfirmadaMercadoRepository transacciones) {
        this.transacciones = Objects.requireNonNull(transacciones);
        this.historialUnificado = null;
    }

    @Override
    public MarketTransactionResult consultar(UUID usuarioId, MarketTransactionFilter filtro) {
        Objects.requireNonNull(usuarioId, "El usuario es obligatorio.");
        Objects.requireNonNull(filtro, "El filtro es obligatorio.");
        validarFiltro(filtro);

        PageRequest pageable = PageRequest.of(filtro.pagina() - 1, filtro.limite());
        if (historialUnificado != null) {
            var page = historialUnificado.buscar(usuarioId, filtro.jugadorId(),
                    filtro.tipoOperacion(), filtro.desde(), filtro.hasta(), pageable);
            List<MarketTransactionRecord> records = page.getContent().stream()
                    .map(MarketTransactionServiceImpl::toRecord)
                    .toList();
            return new MarketTransactionResult(records, new PaginacionResultado(
                    filtro.pagina(), filtro.limite(), page.getTotalPages(),
                    page.getTotalElements()));
        }

        var page = transacciones.buscar(usuarioId, filtro.jugadorId(), legacyType(filtro),
                filtro.desde(), filtro.hasta(), pageable);
        List<MarketTransactionRecord> records = page.getContent().stream()
                .map(MarketTransactionServiceImpl::toRecord)
                .toList();
        return new MarketTransactionResult(records, new PaginacionResultado(
                filtro.pagina(), filtro.limite(), page.getTotalPages(), page.getTotalElements()));
    }

    private static String legacyType(MarketTransactionFilter filtro) {
        return "PRIMARY_PURCHASE".equals(filtro.tipoOperacion())
                ? "PURCHASE" : filtro.tipoOperacion();
    }

    private static void validarFiltro(MarketTransactionFilter filtro) {
        if (filtro.tipoOperacion() != null
                && !filtro.tipoOperacion().equals("PURCHASE")
                && !filtro.tipoOperacion().equals("SALE")
                && !filtro.tipoOperacion().equals("PRIMARY_PURCHASE")
                && !filtro.tipoOperacion().equals("P2P_PURCHASE")
                && !filtro.tipoOperacion().equals("P2P_SALE")) {
            throw new IllegalArgumentException("El tipo de operacion no es valido.");
        }
        if (filtro.desde() != null && filtro.hasta() != null
                && !filtro.desde().isBefore(filtro.hasta())) {
            throw new IllegalArgumentException("El inicio debe ser anterior al fin.");
        }
        if (filtro.pagina() < 1) {
            throw new IllegalArgumentException("La pagina debe ser positiva.");
        }
        if (filtro.limite() < 1 || filtro.limite() > 100) {
            throw new IllegalArgumentException("El limite debe estar entre 1 y 100.");
        }
    }

    private static MarketTransactionRecord toRecord(TransaccionConfirmadaMercadoEntity entity) {
        return new MarketTransactionRecord(
                new MarketTransactionRecord.Identity(entity.getId(), entity.getUsuarioId(),
                        entity.getJugadorId()),
                new MarketTransactionRecord.Snapshot(entity.getNombreJugadorSnapshot(),
                        entity.getTipoOperacion()),
                new MarketTransactionRecord.Amounts(entity.getQuantity(),
                        entity.getPrecioUnitario(), entity.getMontoTotal()),
                new MarketTransactionRecord.Settlement(entity.getContraparteId(),
                        entity.getQuoteId(), entity.getVersionCotizacion()),
                new MarketTransactionRecord.Audit(entity.getSolicitudIdempotenciaId(),
                        entity.getPnlRealizado(), entity.getConfirmadoEn()));
    }

    private static MarketTransactionRecord toRecord(MarketUnifiedTransactionProjection projection) {
        return new MarketTransactionRecord(
                new MarketTransactionRecord.Identity(projection.getTransactionId(),
                        projection.getUserId(), projection.getPlayerId()),
                new MarketTransactionRecord.Snapshot(projection.getPlayerNameSnapshot(),
                        projection.getOperationType()),
                new MarketTransactionRecord.Amounts(projection.getQuantity(),
                        projection.getUnitPrice(), projection.getTotalAmount()),
                new MarketTransactionRecord.Settlement(projection.getCounterpartyId(),
                        projection.getQuoteId(), projection.getQuoteVersion()),
                new MarketTransactionRecord.Audit(projection.getIdempotencyRequestId(),
                        projection.getRealizedPnl(), projection.getConfirmedAt()));
    }
}
