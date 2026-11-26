package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.time.Clock;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Auditoría en los límites de proxy para las operaciones de aplicación seleccionadas explícitamente. */
@Aspect
@Component
public class AuditoriaAspect {
    private final AuditoriaEventWriter writer;
    private final Clock clock;

    @Autowired
    public AuditoriaAspect(AuditoriaEventWriter writer) {
        this(writer, Clock.systemUTC());
    }

    public AuditoriaAspect(AuditoriaEventWriter writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SincronizacionCatalogoServiceImpl.sincronizar())")
    public Object catalogSync(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.CATALOG_SYNC);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SincronizacionEstadisticasJugadorServiceImpl.sincronizar())")
    public Object statisticsSync(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.STATISTICS_SYNC);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.valorizacion.ValorizacionJugadorServiceImpl.ejecutarManual(com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad))")
    public Object valuationManual(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.VALUATION_MANUAL);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.valorizacion.ValorizacionJugadorServiceImpl.ejecutarSemanal(com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad))")
    public Object valuationWeekly(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.VALUATION_WEEKLY);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.market.MarketPurchaseServiceImpl.comprar(com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest))")
    public Object marketPurchase(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.MARKET_PURCHASE);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.market.MarketSaleServiceImpl.vender(com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest))")
    public Object marketSale(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.MARKET_SALE);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2POfferServiceImpl.publicar(com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferCommand))")
    public Object marketP2POffer(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.MARKET_P2P_OFFER_PUBLISH);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2PPurchaseExecutionServiceImpl.comprar(java.util.UUID,java.lang.String,java.util.UUID,java.lang.Long,com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity))")
    public Object marketP2PPurchase(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.MARKET_P2P_PURCHASE);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2PCancellationServiceImpl.cancelar(com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand))")
    public Object marketP2PCancel(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.MARKET_P2P_OFFER_CANCEL);
    }

    @Around("execution(* com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationServiceImpl.initialize())")
    public Object marketInitialize(ProceedingJoinPoint joinPoint) throws Throwable {
        return audit(joinPoint, AuditoriaOperationCatalog.MARKET_INITIALIZE);
    }

    private Object audit(ProceedingJoinPoint joinPoint, String operation) throws Throwable {
        CorrelacionContext context = CorrelacionContext.current().orElse(null);
        boolean installed = context == null;
        if (installed) context = CorrelacionContext.installBackground();
        final CorrelacionContext auditContext = context;
        long started = System.nanoTime();
        try {
            write(() -> writer.write(AuditoriaEvento.start(operation, Instant.now(clock), auditContext)));
            try {
                Object result = joinPoint.proceed();
                long duration = elapsedMillis(started);
                write(() -> writer.write(AuditoriaEvento.end(operation, AuditoriaEvento.Result.SUCCESS,
                        duration, Instant.now(clock), auditContext, null)));
                return result;
            } catch (Throwable error) {
                long duration = elapsedMillis(started);
                String code = AuditoriaErrorCodeResolver.resolve(error);
                write(() -> writer.write(AuditoriaEvento.end(operation, AuditoriaEvento.Result.ERROR,
                        duration, Instant.now(clock), auditContext, code)));
                throw error;
            }
        } finally {
            if (installed) CorrelacionContext.clear();
        }
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private static void write(Runnable action) {
        try { action.run(); } catch (RuntimeException ignored) { /* mejor esfuerzo */ }
    }
}
