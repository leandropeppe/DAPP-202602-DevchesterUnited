import { useCallback, useEffect, useRef, useState } from 'react'
import { getMarketBalance, getMarketPlayers } from '../api/http.js'
import MarketPlayerCard from '../components/MarketPlayerCard.jsx'
import MarketOperationPanel from '../components/MarketOperationPanel.jsx'
import MarketStateMessage from '../components/MarketStateMessage.jsx'
import { useSession } from '../session/SessionContext.jsx'
import { errorMessage } from './formUtils.js'

const EMPTY_MARKET = {
  players: [],
  pagination: { current_page: 1, total_pages: 0, total_records: 0 }
}

function formatBalance (balance) {
  if (balance === null || balance === undefined) return 'Sin información'
  return Number(balance).toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

export default function MarketPage ({ onNavigate = () => {} }) {
  const session = useSession()
  const [page, setPage] = useState(1)
  const [balance, setBalance] = useState(null)
  const [market, setMarket] = useState(EMPTY_MARKET)
  const [state, setState] = useState({ status: 'loading', message: '' })
  const [operation, setOperation] = useState(null)
  const operationPanelRef = useRef(null)

  const loadMarket = useCallback(async (requestedPage, jwt) => {
    if (!jwt || session.isJwtExpired) return
    setState({ status: 'loading', message: '' })
    try {
      const [balanceResult, playersResult] = await Promise.allSettled([
        getMarketBalance(jwt),
        getMarketPlayers({ jwt, filters: { page: requestedPage, limit: 20 } })
      ])

      if (balanceResult.status === 'fulfilled') {
        setBalance(balanceResult.value?.data?.balance ?? null)
      } else {
        setBalance(null)
      }

      if (playersResult.status === 'fulfilled') {
        setMarket(playersResult.value?.data || EMPTY_MARKET)
      } else {
        setMarket(EMPTY_MARKET)
      }

      if (balanceResult.status === 'rejected') {
        throw balanceResult.reason
      }
      if (playersResult.status === 'rejected') {
        throw playersResult.reason
      }

      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({
        status: error?.status === 503 ? 'unavailable' : 'error',
        message: errorMessage(error)
      })
    }
  }, [session.isJwtExpired])

  useEffect(() => {
    if (!session.jwt || session.isJwtExpired) {
      setState({ status: 'error', message: 'Iniciá sesión para consultar el mercado.' })
      return undefined
    }
    void loadMarket(page, session.jwt)
    return undefined
  }, [loadMarket, page, session.isJwtExpired, session.jwt])

  useEffect(() => {
    const panel = operationPanelRef.current
    if (!operation || !panel) return

    const prefersReducedMotion = typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    panel.scrollIntoView?.({
      behavior: prefersReducedMotion ? 'auto' : 'smooth',
      block: 'start'
    })
    panel.focus({ preventScroll: true })
  }, [operation])

  if (!session.jwt || session.isJwtExpired) {
    return (
      <section className="market-page" aria-labelledby="market-title">
        <div className="page-heading">
          <p className="eyebrow">Token Market</p>
          <h1 id="market-title">Mercado de jugadores</h1>
        </div>
        <MarketStateMessage status="error" message={state.message} />
      </section>
    )
  }

  const pagination = market.pagination || EMPTY_MARKET.pagination
  const players = Array.isArray(market.players) ? market.players : []
  const totalPages = Number(pagination.total_pages || 0)

  function go (event, path) {
    event.preventDefault()
    onNavigate(path)
  }

  return (
    <section className="market-page" aria-labelledby="market-title">
      <header className="market-hero">
        <div className="page-heading">
          <p className="eyebrow">Token Market</p>
          <h1 id="market-title">Mercado de jugadores</h1>
          <p>Consultá tu saldo, las cotizaciones publicadas y los tokens disponibles.</p>
        </div>
        <div className="market-balance card" aria-label="Saldo disponible">
          <span className="market-balance__label">Saldo disponible</span>
          <strong>{formatBalance(balance)} créditos</strong>
        </div>
      </header>

      <div className="market-page__links">
        <a className="button button--secondary" href="/market/portfolio" onClick={(event) => go(event, '/market/portfolio')}>Ver portfolio</a>
        <a className="button button--secondary" href="/market/transactions" onClick={(event) => go(event, '/market/transactions')}>Ver historial</a>
      </div>

      {state.status === 'loading' ? <MarketStateMessage status="loading" /> : null}
      {state.status === 'error' || state.status === 'unavailable'
        ? <MarketStateMessage status={state.status} message={state.message} />
        : null}
      {state.status === 'success' && players.length === 0
        ? <output className="empty-state">No hay jugadores disponibles en el mercado.</output>
        : null}
      {players.length > 0
        ? <div className="market-player-grid">{players.map((player) => <MarketPlayerCard key={player.id} player={player} onPurchase={(selected) => setOperation({ player: selected, type: 'PURCHASE' })} />)}</div>
        : null}

      {operation ? <MarketOperationPanel panelRef={operationPanelRef} player={operation.player} type={operation.type} jwt={session.jwt} onClose={() => setOperation(null)} /> : null}

      <nav className="pagination" aria-label="Paginación del mercado">
        <button className="button button--secondary" type="button"
          onClick={() => setPage((current) => Math.max(1, current - 1))}
          disabled={page <= 1 || state.status === 'loading'}>
          Anterior
        </button>
        <span className="pagination__label" aria-live="polite">
          Página {pagination.current_page || page} de {totalPages}
        </span>
        <button className="button button--secondary" type="button"
          onClick={() => setPage((current) => current + 1)}
          disabled={totalPages === 0 || page >= totalPages || state.status === 'loading'}>
          Siguiente
        </button>
      </nav>
    </section>
  )
}
