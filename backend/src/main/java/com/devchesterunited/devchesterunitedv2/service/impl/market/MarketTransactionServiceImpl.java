package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionRecord;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketTransactionService;
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

    public MarketTransactionServiceImpl(TransaccionConfirmadaMercadoRepository transacciones) {
        this.transacciones = Objects.requireNonNull(transacciones);
    }

    @Override
    public MarketTransactionResult consultar(UUID usuarioId,
            MarketTransactionFilter filtro) {
        Objects.requireNonNull(usuarioId, "El usuario es obligatorio.");
        Objects.requireNonNull(filtro, "El filtro es obligatorio.");
        validarFiltro(filtro);

        PageRequest pageable = PageRequest.of(filtro.pagina() - 1, filtro.limite());
        var page = transacciones.buscar(usuarioId, filtro.jugadorId(), filtro.tipoOperacion(),
                filtro.desde(), filtro.hasta(), pageable);
        List<MarketTransactionRecord> records = page.getContent().stream()
                .map(MarketTransactionServiceImpl::toRecord)
                .toList();
        PaginacionResultado paginacion = new PaginacionResultado(filtro.pagina(), filtro.limite(),
                page.getTotalPages(), page.getTotalElements());
        return new MarketTransactionResult(records, paginacion);
    }

    private static void validarFiltro(MarketTransactionFilter filtro) {
        if (filtro.tipoOperacion() != null
                && !filtro.tipoOperacion().equals("PURCHASE")
                && !filtro.tipoOperacion().equals("SALE")) {
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
        return new MarketTransactionRecord(entity.getId(), entity.getUsuarioId(),
                entity.getJugadorId(), entity.getNombreJugadorSnapshot(), entity.getTipoOperacion(),
                entity.getQuantity(), entity.getPrecioUnitario(), entity.getMontoTotal(),
                entity.getContraparteId(), entity.getQuoteId(), entity.getVersionCotizacion(),
                entity.getSolicitudIdempotenciaId(), entity.getPnlRealizado(),
                entity.getConfirmadoEn());
    }
}
