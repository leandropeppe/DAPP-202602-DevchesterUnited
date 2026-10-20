import { useCallback, useEffect, useState } from 'react'
import { getMarketTransactions } from '../api/http.js'
import MarketStateMessage from '../components/MarketStateMessage.jsx'
import { marketPlayerUuid } from '../market/marketState.js'
import { useSession } from '../session/SessionContext.jsx'
import { errorMessage } from './formUtils.js'

const EMPTY_RESULT = { transactions: [], pagination: { current_page: 1, total_pages: 0, total_records: 0 } }

function dateFilter (value) {
  return value ? new Date(value).toISOString() : undefined
}

function amount (value) {
  if (value === null || value === undefined) return '—'
  return Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function isPagination (value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) && ['current_page', 'page_size', 'total_pages', 'total_records'].every((field) => Number.isFinite(value[field]))
}

function isTransactionsResponse (response) {
  const data = response?.data
  return data !== null && typeof data === 'object' && !Array.isArray(data) && Array.isArray(data.transactions) && isPagination(data.pagination)
}

function operationLabel (operationType) {
  if (operationType === 'PURCHASE') return 'Compra'
  if (operationType === 'SALE') return 'Venta'
  return `Tipo de operación no reconocido${operationType ? `: ${operationType}` : ''}`
}

export default function MarketTransactionsPage () {
  const session = useSession()
  const [filters, setFilters] = useState({ playerId: '', type: '', from: '', to: '' })
  const [appliedFilters, setAppliedFilters] = useState({ playerId: '', type: '', from: '', to: '' })
  const [page, setPage] = useState(1)
  const [result, setResult] = useState(EMPTY_RESULT)
  const [state, setState] = useState({ status: 'loading', message: '' })

  const load = useCallback(async (requestedPage, requestedFilters) => {
    if (!session.jwt || session.isJwtExpired) return
    setState({ status: 'loading', message: '' })
    try {
      const playerId = requestedFilters.playerId ? marketPlayerUuid(requestedFilters.playerId) : undefined
      const response = await getMarketTransactions({
        jwt: session.jwt,
        filters: {
          playerId: playerId || requestedFilters.playerId || undefined,
          type: requestedFilters.type || undefined,
          from: dateFilter(requestedFilters.from),
          to: dateFilter(requestedFilters.to),
          page: requestedPage,
          limit: 20
        }
      })
      if (!isTransactionsResponse(response)) throw new Error('El servidor devolvió un historial incompleto.')
      setResult(response.data)
      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({ status: error?.status === 503 ? 'unavailable' : 'error', message: errorMessage(error) })
    }
  }, [session.isJwtExpired, session.jwt])

  useEffect(() => { void load(page, appliedFilters) }, [appliedFilters, load, page])

  if (!session.jwt || session.isJwtExpired) return <MarketStateMessage status="error" message="Iniciá sesión para consultar tu historial." />
  if (state.status === 'loading') return <MarketStateMessage status="loading" />
  if (state.status === 'error' || state.status === 'unavailable') return <MarketStateMessage status={state.status} message={state.message} />

  const pagination = result.pagination || EMPTY_RESULT.pagination
  const transactions = Array.isArray(result.transactions) ? result.transactions : []
  function update (event) { setFilters((current) => ({ ...current, [event.target.name]: event.target.value })) }
  function submit (event) { event.preventDefault(); setPage(1); setAppliedFilters(filters) }

  return (
    <section className="market-page" aria-labelledby="transactions-title">
      <div className="page-heading"><p className="eyebrow">Token Market</p><h1 id="transactions-title">Historial de transacciones</h1><p>Solo se muestran operaciones confirmadas de tu cuenta.</p></div>
      <form className="card market-filters" onSubmit={submit}>
        <label className="field">Jugador<input name="playerId" value={filters.playerId} onChange={update} placeholder="UUID o ply_UUID" /></label>
        <label className="field">Tipo<select name="type" value={filters.type} onChange={update}><option value="">Todos</option><option value="PURCHASE">Compras</option><option value="SALE">Ventas</option></select></label>
        <label className="field">Desde<input type="datetime-local" name="from" value={filters.from} onChange={update} /></label>
        <label className="field">Hasta<input type="datetime-local" name="to" value={filters.to} onChange={update} /></label>
        <button className="button button--primary" type="submit">Aplicar filtros</button>
      </form>
      {transactions.length === 0
        ? <output className="empty-state">No hay transacciones confirmadas para estos filtros.</output>
        : <div className="market-transactions-list">{transactions.map((transaction) => <article className="card market-transaction-card" key={transaction.transaction_id}><div><strong>{operationLabel(transaction.operation_type)}</strong><span>{transaction.confirmed_at}</span></div><p className="market-transaction-card__snapshot">{transaction.player_name_snapshot} · {transaction.player_id}</p><dl><div><dt>Cantidad</dt><dd>{transaction.quantity}</dd></div><div><dt>Precio unitario</dt><dd>{amount(transaction.unit_price)}</dd></div><div><dt>Total</dt><dd>{amount(transaction.total_amount)}</dd></div><div><dt>Versión cotización</dt><dd>{transaction.quote_version}</dd></div><div><dt>P/L realizada</dt><dd>{amount(transaction.realized_pnl)}</dd></div></dl></article>)}</div>}
      <nav className="pagination" aria-label="Paginación del historial"><button className="button button--secondary" type="button" onClick={() => setPage((current) => Math.max(1, current - 1))} disabled={page <= 1}>Anterior</button><span className="pagination__label">Página {pagination.current_page || page} de {pagination.total_pages || 0}</span><button className="button button--secondary" type="button" onClick={() => setPage((current) => current + 1)} disabled={!pagination.total_pages || page >= pagination.total_pages}>Siguiente</button></nav>
    </section>
  )
}
