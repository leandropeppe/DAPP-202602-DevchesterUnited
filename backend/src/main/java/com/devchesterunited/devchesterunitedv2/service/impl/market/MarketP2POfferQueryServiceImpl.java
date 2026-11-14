package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketP2POfferProjection;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferPageResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferQuery;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferView;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Implementa el listado P2P sin mutar ofertas, reservas ni cotizaciones. */
@Service
public class MarketP2POfferQueryServiceImpl implements MarketP2POfferQueryService {
    private final OfertaVentaP2PRepository offers;
    private final Clock clock;

    public MarketP2POfferQueryServiceImpl(OfertaVentaP2PRepository offers, Clock clock) {
        this.offers = offers;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MarketP2POfferPageResult consultar(MarketP2POfferQuery query) {
        var now = clock.instant();
        PageRequest pageRequest = PageRequest.of(query.pagina() - 1, query.limite());
        Page<MarketP2POfferProjection> page = query.nombreJugador() == null
                ? offers.buscarPublico(query.jugadorId(), query.estado(), query.soloMias(),
                query.usuarioId(), now, pageRequest)
                : offers.buscarPublico(query.jugadorId(), query.nombreJugador(), query.estado(),
                query.soloMias(), query.usuarioId(), now, pageRequest);
        List<MarketP2POfferView> views = page.getContent().stream()
                .map(offer -> MarketP2POfferViewMapper.toView(offer, now, query.usuarioId()))
                .toList();
        PaginacionResultado pagination = new PaginacionResultado(query.pagina(), query.limite(),
                page.getTotalPages(), page.getTotalElements());
        return new MarketP2POfferPageResult(views, pagination);
    }
}
