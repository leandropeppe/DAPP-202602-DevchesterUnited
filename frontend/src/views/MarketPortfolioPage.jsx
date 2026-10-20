import { useCallback, useEffect, useState } from 'react'
import { getMarketPortfolio } from '../api/http.js'
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
  return data !== null && typeof data === 'object' && !Array.isArray(data) && Array.isArray(data.open_positions) && data.realized_pnl !== null && data.realized_pnl !== undefined && typeof data.combined_result_complete === 'boolean'
}

export default function MarketPortfolioPage () {
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
    return <MarketStateMessage status="error" message="Iniciá sesión para consultar tu portfolio." />
  }
  if (state.status === 'loading') return <MarketStateMessage status="loading" />
  if (state.status === 'error' || state.status === 'unavailable') return <MarketStateMessage status={state.status} message={state.message} />

  const positions = Array.isArray(portfolio?.open_positions) ? portfolio.open_positions : []
  return (
    <section className="market-page" aria-labelledby="portfolio-title">
      <div className="page-heading">
        <p className="eyebrow">Token Market</p>
        <h1 id="portfolio-title">Mi portfolio</h1>
        <p>Posiciones abiertas y resultados entregados por el mercado.</p>
      </div>
      <div className="market-summary-grid">
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
                <div><h2>Jugador {position.player_id}</h2><span>{position.quantity} tokens</span></div>
                <dl className="market-position-card__facts">
                  <div><dt>Costo pendiente</dt><dd>{amount(position.pending_acquisition_cost)}</dd></div>
                  <div><dt>Costo promedio</dt><dd>{amount(position.weighted_average_cost)}</dd></div>
                  <div><dt>Última cotización</dt><dd>{amount(position.unit_price)}</dd></div>
                  <div><dt>Valor actual</dt><dd>{amount(position.current_value)}</dd></div>
                  <div><dt>P/L no realizada</dt><dd>{amount(position.unrealized_pnl)}</dd></div>
                </dl>
                <QuoteStatus status={position.quote_availability} price={position.unit_price === null ? null : amount(position.unit_price)} />
              </article>
            ))}
          </div>
        )}
    </section>
  )
}
