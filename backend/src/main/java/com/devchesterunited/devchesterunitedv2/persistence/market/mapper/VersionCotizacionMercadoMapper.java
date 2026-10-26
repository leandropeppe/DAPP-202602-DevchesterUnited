package com.devchesterunited.devchesterunitedv2.persistence.market.mapper;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Traduce una versión persistida de cotización al modelo de servicio. */
@Component
public class VersionCotizacionMercadoMapper {

    /**
     * Mapea la identidad y los valores de una cotización sin modificar la entidad
     * persistida ni crear una nueva versión.
     */
    public QuoteVersion toDomain(VersionCotizacionMercadoEntity entidad) {
        Objects.requireNonNull(entidad, "entidad es obligatoria");
        Objects.requireNonNull(entidad.getId(), "la clave de cotización es obligatoria");

        return new QuoteVersion(
                entidad.getId().getQuoteId(),
                entidad.getId().getVersion(),
                entidad.getJugadorId(),
                entidad.getPrecioPorToken(),
                entidad.getPublicadoEn(),
                entidad.getValidoDesde(),
                entidad.getValidoHasta(),
                QuoteValidityStatus.valueOf(entidad.getEstadoVigencia()),
                entidad.getReferenciaEstrategia());
    }
}
