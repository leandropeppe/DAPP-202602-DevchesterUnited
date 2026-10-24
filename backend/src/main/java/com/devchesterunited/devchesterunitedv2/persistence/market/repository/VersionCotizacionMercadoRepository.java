package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VersionCotizacionMercadoRepository
        extends JpaRepository<VersionCotizacionMercadoEntity, VersionCotizacionMercadoId> {
    @Query("select cotizacion from VersionCotizacionMercadoEntity cotizacion "
            + "where cotizacion.jugadorId = :jugadorId "
            + "and cotizacion.id.quoteId = :quoteId "
            + "and cotizacion.id.version = :version")
    Optional<VersionCotizacionMercadoEntity> findExact(
            @Param("jugadorId") UUID jugadorId, @Param("quoteId") UUID quoteId,
            @Param("version") Long version);

    @Query("select cotizacion from VersionCotizacionMercadoEntity cotizacion "
            + "where cotizacion.jugadorId = :jugadorId "
            + "and cotizacion.id.quoteId = :quoteId "
            + "and cotizacion.id.version = :version "
            + "and cotizacion.estadoVigencia = 'VALID' "
            + "and cotizacion.validoDesde <= :momento "
            + "and (cotizacion.validoHasta is null or cotizacion.validoHasta > :momento) "
            + "and exists (select cabecera from CabeceraCotizacionMercadoEntity cabecera "
            + "where cabecera.jugadorId = cotizacion.jugadorId "
            + "and cabecera.quoteId = cotizacion.id.quoteId "
            + "and cabecera.versionActual = cotizacion.id.version "
            + "and cabecera.estadoVigencia = 'VALID')")
    Optional<VersionCotizacionMercadoEntity> findExactVigente(
            @Param("jugadorId") UUID jugadorId, @Param("quoteId") UUID quoteId,
            @Param("version") Long version, @Param("momento") Instant momento);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select cotizacion from VersionCotizacionMercadoEntity cotizacion "
            + "where cotizacion.jugadorId = :jugadorId "
            + "and cotizacion.id.quoteId = :quoteId "
            + "and cotizacion.id.version = :version")
    Optional<VersionCotizacionMercadoEntity> findExactForRead(
            @Param("jugadorId") UUID jugadorId, @Param("quoteId") UUID quoteId,
            @Param("version") Long version);

    List<VersionCotizacionMercadoEntity> findAllByJugadorIdOrderByPublicadoEnDesc(UUID jugadorId);
}
