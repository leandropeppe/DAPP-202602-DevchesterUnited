import { useCallback, useEffect, useState } from 'react'
import { getMarketPortfolio } from '../api/http.js'
import MarketPortfolioCharts from '../components/MarketPortfolioCharts.jsx'
import MarketStateMessage from '../components/MarketStateMessage.jsx'
import QuoteStatus from '../components/QuoteStatus.jsx'
import { useSession } from '../session/SessionContext.jsx'
import { errorMessage } from './formUtils.js'

function amount (value) {
  if (value === null || value === undefined) return 'Sin información'
  return Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function isPortfolioResponse (response) {
  const data = response?.data
  return data !== null && typeof data === 'object' && !Array.isArray(data) &&
    Array.isArray(data.open_positions) &&
    data.open_players !== null && data.open_players !== undefined &&
    data.total_tokens !== null && data.total_tokens !== undefined &&
    data.total_pending_acquisition_cost !== null && data.total_pending_acquisition_cost !== undefined &&
    Object.hasOwn(data, 'total_current_value') &&
    data.realized_pnl !== null && data.realized_pnl !== undefined &&
    typeof data.combined_result_complete === 'boolean'
}

function positionName (position) {
  return position.player_name || `Jugador ${position.player_id}`
}

function positionQuantities (position) {
  const total = Number(position.total_quantity ?? position.quantity ?? 0)
  const reserved = Number(position.reserved_quantity ?? 0)
  const available = Number(position.available_quantity ?? (total - reserved))
  return { total, reserved, available }
}

function canPublish (position) {
  const quantities = positionQuantities(position)
  return position.quote_availability === 'CURRENT' &&
    position.quote_id !== null && position.quote_id !== undefined && position.quote_id !== '' &&
    position.quote_version !== null && position.quote_version !== undefined &&
    position.unit_price !== null && position.unit_price !== undefined &&
    Number.isInteger(quantities.available) && quantities.available > 0
}

export default function MarketPortfolioPage ({ onNavigate }) {
  const session = useSession()
  const [state, setState] = useState({ status: 'loading', message: '' })
  const [portfolio, setPortfolio] = useState(null)

  const load = useCallback(async () => {
    if (!session.jwt || session.isJwtExpired) return
    setState({ status: 'loading', message: '' })
    try {
      const response = await getMarketPortfolio(session.jwt)
      if (!isPortfolioResponse(response)) throw new Error('El servidor devolvió un portfolio incompleto.')
      setPortfolio(response.data)
      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({ status: error?.status === 503 ? 'unavailable' : 'error', message: errorMessage(error) })
    }
  }, [session.isJwtExpired, session.jwt])

  useEffect(() => { void load() }, [load])

  if (!session.jwt || session.isJwtExpired) {
    return <section className="market-page" aria-labelledby="portfolio-title"><MarketStateMessage status="error" message="Iniciá sesión para consultar tu portfolio." /></section>
  }
  if (state.status === 'loading') return <section className="market-page" aria-labelledby="portfolio-title"><MarketStateMessage status="loading" /></section>
  if (state.status === 'error' || state.status === 'unavailable') return <section className="market-page" aria-labelledby="portfolio-title"><MarketStateMessage status={state.status} message={state.message} /></section>

  const positions = Array.isArray(portfolio?.open_positions) ? portfolio.open_positions : []

  return (
    <section className="market-page" aria-labelledby="portfolio-title">
      <div className="page-heading">
        <p className="eyebrow">Token Market</p>
        <h1 id="portfolio-title">Mi portfolio</h1>
        <p>Posiciones abiertas y resultados entregados por el mercado.</p>
      </div>
      <div className="market-summary-grid">
        <div className="card market-summary-card"><span>Jugadores con posición</span><strong>{amount(portfolio?.open_players)}</strong></div>
        <div className="card market-summary-card"><span>Tokens totales</span><strong>{amount(portfolio?.total_tokens)}</strong></div>
        <div className="card market-summary-card"><span>Costo pendiente total</span><strong>{amount(portfolio?.total_pending_acquisition_cost)}</strong></div>
        <div className="card market-summary-card"><span>Valor actual total</span><strong>{amount(portfolio?.total_current_value)}</strong></div>
        <div className="card market-summary-card"><span>P/L realizada</span><strong>{amount(portfolio?.realized_pnl)}</strong></div>
        <div className="card market-summary-card"><span>P/L no realizada</span><strong>{amount(portfolio?.unrealized_pnl)}</strong></div>
        <div className="card market-summary-card"><span>Resultado combinado</span><strong>{amount(portfolio?.combined_result)}</strong></div>
      </div>
      {positions.length === 0
        ? <output className="empty-state">No tenés posiciones abiertas.</output>
        : (
          <div className="market-positions-list">
            {positions.map((position) => (
              <article className="card market-position-card" key={position.player_id}>
                {(() => { const quantities = positionQuantities(position); return <>
                <div>
                  <h2>{positionName(position)}</h2>
                  <span>{quantities.total} tokens</span>
                  {position.team || position.league ? <p>{position.team || 'Equipo no disponible'} · {position.league || 'Liga no disponible'}</p> : null}
                </div>
                <dl className="market-position-card__facts">
                  <div><dt>Total</dt><dd>{quantities.total}</dd></div>
                  <div><dt>Reservados</dt><dd>{quantities.reserved}</dd></div>
                  <div><dt>Disponibles</dt><dd>{quantities.available}</dd></div>
                  <div><dt>Costo pendiente</dt><dd>{amount(position.pending_acquisition_cost)}</dd></div>
                  <div><dt>Costo promedio</dt><dd>{amount(position.weighted_average_cost)}</dd></div>
                  <div><dt>Última cotización</dt><dd>{amount(position.unit_price)}</dd></div>
                  <div><dt>Valor actual</dt><dd>{amount(position.current_value)}</dd></div>
                  <div><dt>P/L no realizada</dt><dd>{amount(position.unrealized_pnl)}</dd></div>
                </dl>
                <QuoteStatus status={position.quote_availability} price={position.unit_price === null ? null : amount(position.unit_price)} />
                {canPublish(position)
                  ? <button className="button button--primary" type="button" onClick={() => onNavigate?.(`/market/p2p/offers?publishPlayerId=${encodeURIComponent(position.player_id)}`)}>Publicar oferta P2P</button>
                  : null}
                </> })()}
              </article>
            ))}
          </div>
        )}
      <MarketPortfolioCharts positions={positions} />
    </section>
  )
}
