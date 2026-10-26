package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaRechazoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AuditoriaRechazoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketRejectionAuditService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketRejectionAuditTransactionalWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class MarketRejectionAuditServiceImpl implements MarketRejectionAuditService,
        MarketRejectionAuditTransactionalWriter {
    private final AuditoriaRechazoMercadoRepository auditoria;

    public MarketRejectionAuditServiceImpl(AuditoriaRechazoMercadoRepository auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(MarketRejectedAttempt attempt) {
        registrarInterno(attempt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarEnTransaccionActual(MarketRejectedAttempt attempt) {
        registrarInterno(attempt);
    }

    private void registrarInterno(MarketRejectedAttempt attempt) {
        Objects.requireNonNull(attempt, "El intento rechazado es obligatorio.");
        auditoria.save(new AuditoriaRechazoMercadoEntity(
                UUID.randomUUID(), attempt.usuarioId(), attempt.tipoOperacion(),
                attempt.jugadorId(), attempt.hashClaveIdempotencia(), attempt.codigoMotivo(),
                attempt.hashParametros(), attempt.creadoEn()));
    }
}
