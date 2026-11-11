import { useCallback, useEffect, useRef, useState } from 'react'
import { getMarketPortfolio, getPlayerDetail } from '../api/http.js'
import MarketPortfolioCharts from '../components/MarketPortfolioCharts.jsx'
import MarketPortfolioPlayerCard from '../components/MarketPortfolioPlayerCard.jsx'
import MarketStateMessage from '../components/MarketStateMessage.jsx'
import { useSession } from '../session/SessionContext.jsx'
import { errorMessage } from './formUtils.js'

const PAGE_SIZES = [6, 12, 24]

function amount (value) {
  if (value === null || value === undefined) return 'Sin información'
  return Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function metricTone (value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number === 0) return 'neutral'
  return number > 0 ? 'positive' : 'negative'
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

function pageWindow (currentPage, totalPages) {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index + 1)
  let start = Math.max(2, currentPage - 2)
  let end = Math.min(totalPages - 1, currentPage + 2)
  if (currentPage <= 3) end = 5
  if (currentPage >= totalPages - 2) start = totalPages - 4
  return [
    1,
    ...(start > 2 ? ['ellipsis'] : []),
    ...Array.from({ length: end - start + 1 }, (_, index) => start + index),
    ...(end < totalPages - 1 ? ['ellipsis'] : []),
    totalPages
  ]
}

const METRICS = [
  ['open_players', 'Jugadores con posición', 'Cantidad de jugadores con tokens en cartera.'],
  ['total_tokens', 'Tokens totales', 'Tokens pertenecientes a tus posiciones abiertas.'],
  ['total_pending_acquisition_cost', 'Costo pendiente total', 'Costo acumulado de adquisición de tus tokens.'],
  ['total_current_value', 'Valor actual total', 'Valor oficial de mercado de tus posiciones actuales.'],
  ['realized_pnl', 'P/L realizada', 'Resultado obtenido en operaciones ya cerradas.'],
  ['unrealized_pnl', 'P/L no realizada', 'Resultado estimado según las cotizaciones actuales.'],
  ['combined_result', 'Resultado combinado', 'Resultado total cuando el mercado dispone de todos los datos.']
]

export default function MarketPortfolioPage ({ onNavigate }) {
  const session = useSession()
  const [state, setState] = useState({ status: 'loading', message: '' })
  const [portfolio, setPortfolio] = useState(null)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(6)
  const [catalogData, setCatalogData] = useState({})
  const catalogCache = useRef(new Map())

  const load = useCallback(async () => {
    if (!session.jwt || session.isJwtExpired) return
    setState({ status: 'loading', message: '' })
    try {
      const response = await getMarketPortfolio(session.jwt)
      if (!isPortfolioResponse(response)) throw new Error('El servidor devolvió un portfolio incompleto.')
      setPortfolio(response.data)
      setPage(1)
      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({ status: error?.status === 503 ? 'unavailable' : 'error', message: errorMessage(error) })
    }
  }, [session.isJwtExpired, session.jwt])

  useEffect(() => { void load() }, [load])

  const positions = Array.isArray(portfolio?.open_positions) ? portfolio.open_positions : []
  const totalPages = Math.ceil(positions.length / pageSize)
  const currentPage = totalPages === 0 ? 1 : Math.min(page, totalPages)
  const visiblePositions = positions.slice((currentPage - 1) * pageSize, currentPage * pageSize)
  const firstRecord = positions.length === 0 ? 0 : ((currentPage - 1) * pageSize) + 1
  const lastRecord = Math.min(currentPage * pageSize, positions.length)

  useEffect(() => {
    if (typeof getPlayerDetail !== 'function' || visiblePositions.length === 0) return

    let active = true
    const pending = visiblePositions
      .filter(position => !catalogCache.current.has(position.player_id) &&
        (position.position === undefined || position.position === null || position.position === '' ||
          position.valuation === undefined || position.valuation === null || position.valuation === '' ||
          position.nationality === undefined || position.nationality === null || position.nationality === ''))
      .map(async position => {
        const playerId = String(position.player_id || '')
        const catalogId = playerId.startsWith('ply_') ? playerId : `ply_${playerId}`
        try {
          const response = await getPlayerDetail(catalogId)
          const player = response?.data
          const metadata = player && typeof player === 'object'
            ? { position: player.position, valuation: player.valuation, nationality: player.nationality }
            : null
          catalogCache.current.set(position.player_id, metadata)
          return [position.player_id, metadata]
        } catch {
          catalogCache.current.set(position.player_id, null)
          return [position.player_id, null]
        }
      })

    if (pending.length === 0) return () => { active = false }
    Promise.all(pending).then(entries => {
      if (!active) return
      setCatalogData(current => Object.fromEntries([...Object.entries(current), ...entries]))
    })
    return () => { active = false }
  }, [portfolio, currentPage, pageSize])

  const positionWithCatalogData = position => ({
    ...position,
    ...(catalogData[position.player_id] || {})
  })

  if (!session.jwt || session.isJwtExpired) {
    return <section className="market-page" aria-labelledby="portfolio-title"><MarketStateMessage status="error" message="Iniciá sesión para consultar tu portfolio." /></section>
  }
  if (state.status === 'loading') {
    return <section className="market-page" aria-labelledby="portfolio-title"><MarketStateMessage status="loading" /></section>
  }
  if (state.status === 'error' || state.status === 'unavailable') {
    return <section className="market-page" aria-labelledby="portfolio-title"><MarketStateMessage status={state.status} message={state.message} /></section>
  }

  return (
    <section className="market-page" aria-labelledby="portfolio-title">
      <div className="page-heading">
        <p className="eyebrow">Token Market</p>
        <h1 id="portfolio-title">Mi portfolio</h1>
        <p>Posiciones abiertas y resultados entregados por el mercado.</p>
      </div>

      <div className="market-summary-grid market-portfolio-summary-grid">
        {METRICS.map(([key, label, description]) => (
          <article className={`card market-summary-card market-portfolio-summary-card market-portfolio-summary-card--${metricTone(portfolio?.[key])}`} key={key}>
            <span>{label}</span>
            <strong>{amount(portfolio?.[key])}</strong>
            <small>{description}</small>
          </article>
        ))}
      </div>

      {positions.length === 0
        ? <output className="empty-state">No tenés posiciones abiertas.</output>
        : (
          <>
            <div className="market-portfolio-positions-heading">
              <div>
                <h2>Mis jugadores</h2>
                <p>Disponibilidad, cotizaciones y resultado por posición.</p>
              </div>
              <span>Mostrando {firstRecord}–{lastRecord} de {positions.length} jugadores</span>
            </div>
            <div className="market-portfolio-players-grid">
              {visiblePositions.map(position => <MarketPortfolioPlayerCard key={position.player_id} position={positionWithCatalogData(position)} onNavigate={onNavigate} />)}
            </div>
            <nav className="pagination market-portfolio-pagination" aria-label="Paginación de posiciones">
              <div className="market-portfolio-pagination__summary">
                <span>Mostrando {firstRecord}–{lastRecord} de {positions.length} jugadores</span>
                <label>
                  <span>Jugadores por página</span>
                  <select aria-label="Jugadores por página" value={pageSize} onChange={event => { setPageSize(Number(event.target.value)); setPage(1) }}>
                    {PAGE_SIZES.map(size => <option key={size} value={size}>{size}</option>)}
                  </select>
                </label>
              </div>
              <div className="market-portfolio-pagination__controls">
                <button className="button button--secondary" type="button" onClick={() => setPage(current => Math.max(1, current - 1))} disabled={currentPage <= 1}>Anterior</button>
                <div className="market-portfolio-pagination__pages" aria-label="Páginas">
                  {pageWindow(currentPage, totalPages).map((item, index) => item === 'ellipsis'
                    ? <span key={`ellipsis-${index}`} aria-hidden="true">…</span>
                    : <button key={item} className={`button ${item === currentPage ? 'button--primary' : 'button--secondary'}`} type="button" aria-current={item === currentPage ? 'page' : undefined} onClick={() => setPage(item)} disabled={item === currentPage}>{item}</button>)}
                </div>
                <button className="button button--secondary" type="button" onClick={() => setPage(current => Math.min(totalPages, current + 1))} disabled={!totalPages || currentPage >= totalPages}>Siguiente</button>
              </div>
            </nav>
          </>
        )}

      <MarketPortfolioCharts positions={positions} />
    </section>
  )
}
