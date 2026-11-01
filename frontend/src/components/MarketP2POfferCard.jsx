function formatAmount (value) {
  if (value === null || value === undefined) return 'No disponible'
  return Number(value).toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 8
  })
}

const STATUS_LABELS = {
  PURCHASABLE: 'Disponible para comprar',
  SUSPENDED: 'Suspendida',
  FILLED: 'Completada',
  CANCELLED: 'Cancelada'
}

function getActionLabel (ownOffer, status) {
  if (ownOffer) return 'Cancelar oferta'
  if (status === 'PURCHASABLE') return 'Comprar oferta'
  return 'Operación no disponible'
}

export default function MarketP2POfferCard ({ offer, onAction }) {
  const status = offer?.status || 'SUSPENDED'
  const quote = offer?.quote
  const statusLabel = STATUS_LABELS[status] || status
  const ownOffer = Boolean(offer?.mine)
  const actionLabel = getActionLabel(ownOffer, status)
  const actionEnabled = (ownOffer && ['OPEN', 'PARTIALLY_FILLED', 'PURCHASABLE', 'SUSPENDED'].includes(status)) ||
    (!ownOffer && status === 'PURCHASABLE')

  return (
    <article className="card market-p2p-offer-card" data-status={status}>
      <div className="market-p2p-offer-card__heading">
        <div>
          <p className="eyebrow">Oferta P2P</p>
          <h2>{offer?.playerName || `Jugador ${offer?.playerId || 'sin identificar'}`}</h2>
        </div>
        <span className={`market-p2p-status market-p2p-status--${status.toLowerCase()}`}>{statusLabel}</span>
      </div>

      <dl className="market-p2p-offer-card__facts">
        <div><dt>Vendedor</dt><dd>{ownOffer ? 'Tu oferta' : (offer?.sellerAlias || 'Alias no disponible')}</dd></div>
        <div><dt>Cantidad disponible</dt><dd>{formatAmount(offer?.remainingQuantity)}</dd></div>
        <div><dt>Cotización oficial</dt><dd>{quote ? formatAmount(quote.unitPrice) : 'No disponible'}</dd></div>
        {quote ? <div><dt>Versión</dt><dd>{quote.version}</dd></div> : null}
      </dl>

      <div className="market-p2p-offer-card__actions">
        <button className="button button--buy" type="button" onClick={() => onAction?.(offer)} disabled={!actionEnabled} aria-disabled={!actionEnabled}>
          {actionLabel}
        </button>
      </div>
    </article>
  )
}
