import QuoteStatus from './QuoteStatus.jsx'
import { isMarketPlayerOperable, quotePresentationStatus } from '../market/marketState.js'

function displayValue (value) {
  return value === null || value === undefined || value === '' ? 'Sin información' : value
}

function formatPrice (price) {
  if (price === null || price === undefined || price === '') return null
  return Number(price).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function MarketPlayerCard ({ player, onPurchase }) {
  const quoteStatus = quotePresentationStatus(player)
  const price = formatPrice(player.price_per_token)
  const operable = isMarketPlayerOperable(player)

  return (
    <article className="market-player-card card" aria-labelledby={`market-player-${player.id}`}>
      <div className="market-player-card__heading">
        <div>
          <p className="eyebrow">Jugador de mercado</p>
          <h2 id={`market-player-${player.id}`}>{displayValue(player.name)}</h2>
        </div>
        <span className={`market-player-card__availability ${operable ? 'is-operable' : 'is-unavailable'}`}>
          {operable ? 'Operable' : 'No operable'}
        </span>
      </div>
      <p className="market-player-card__meta">{displayValue(player.team)} · {displayValue(player.league)}</p>
      <dl className="market-player-card__facts">
        <div><dt>Precio por token</dt><dd>{price === null ? 'Sin cotización' : price}</dd></div>
        <div><dt>Tokens disponibles</dt><dd>{displayValue(player.tokens_available)}</dd></div>
        <div><dt>Versión</dt><dd>{displayValue(player.quote_version)}</dd></div>
      </dl>
      <QuoteStatus status={quoteStatus} price={price} />
      {operable
        ? (
              <div className="market-player-card__actions">
                  <button
                      className="button button--buy"
                      type="button"
                      onClick={() => onPurchase?.(player)}
                  >
                      Comprar
                  </button>
              </div>
          )
          : null}
        {!player.player_active || !player.team_active
            ? <p className="market-player-card__notice">El jugador o su equipo no están activos.</p>
            : null}
    </article>
  )
}
