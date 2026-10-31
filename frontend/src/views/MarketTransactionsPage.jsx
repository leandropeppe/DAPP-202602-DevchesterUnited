import { useCallback, useEffect, useState } from 'react'
import { getMarketTransactions } from '../api/http.js'
import MarketStateMessage from '../components/MarketStateMessage.jsx'
import MarketTransactionActivityChart from '../components/MarketTransactionActivityChart.jsx'
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
  if (operationType === 'PRIMARY_PURCHASE' || operationType === 'PURCHASE') return 'Compra primaria'
  if (operationType === 'SALE') return 'Venta legacy'
  if (operationType === 'P2P_PURCHASE') return 'Compra P2P'
  if (operationType === 'P2P_SALE') return 'Venta P2P'
  const operationSuffix = operationType ? `: ${operationType}` : ''
  return `Tipo de operaci\u00f3n no reconocido${operationSuffix}`
}

function showsRealizedPnl (transaction) {
  return transaction?.realized_pnl !== null && transaction?.realized_pnl !== undefined
}

function transactionPlayerName (transaction) {
  const name = transaction?.player_name ?? transaction?.player_name_snapshot
  return typeof name === 'string' && name.trim() ? name.trim() : 'Jugador sin nombre disponible'
}

function playerOptionKey (name) {
  return name.toLocaleLowerCase()
}

function transactionDate (value) {
  if (!value) return 'Fecha no disponible'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}

export default function MarketTransactionsPage () {
  const session = useSession()
  const [filters, setFilters] = useState({ playerId: '', type: '', from: '', to: '' })
  const [appliedFilters, setAppliedFilters] = useState({ playerId: '', type: '', from: '', to: '' })
  const [page, setPage] = useState(1)
  const [result, setResult] = useState(EMPTY_RESULT)
  const [playerOptions, setPlayerOptions] = useState([])
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
      setPlayerOptions((current) => {
        const next = new Map(current.map((option) => [option.id, option]))
        for (const transaction of response.data.transactions) {
          const id = marketPlayerUuid(transaction.player_id)
          if (!id) continue
          next.set(id, { id, name: transactionPlayerName(transaction) })
        }
        return [...next.values()].sort((left, right) => left.name.localeCompare(right.name, 'es'))
      })
      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({ status: error?.status === 503 ? 'unavailable' : 'error', message: errorMessage(error) })
    }
  }, [session.isJwtExpired, session.jwt])

  useEffect(() => { void load(page, appliedFilters) }, [appliedFilters, load, page])

  if (!session.jwt || session.isJwtExpired) return <section className="market-page" aria-labelledby="transactions-title"><MarketStateMessage status="error" message="Iniciá sesión para consultar tu historial." /></section>
  if (state.status === 'loading') return <section className="market-page" aria-labelledby="transactions-title"><MarketStateMessage status="loading" /></section>
  if (state.status === 'error' || state.status === 'unavailable') return <section className="market-page" aria-labelledby="transactions-title"><MarketStateMessage status={state.status} message={state.message} /></section>

  const pagination = result.pagination || EMPTY_RESULT.pagination
  const transactions = Array.isArray(result.transactions) ? result.transactions : []
  function update (event) { setFilters((current) => ({ ...current, [event.target.name]: event.target.value })) }
  function submit (event) { event.preventDefault(); setPage(1); setAppliedFilters(filters) }
  function clearFilters () {
    const emptyFilters = { playerId: '', type: '', from: '', to: '' }
    setFilters(emptyFilters)
    setPage(1)
    setAppliedFilters(emptyFilters)
  }

  const duplicateNames = playerOptions.reduce((counts, option) => {
    const key = playerOptionKey(option.name)
    counts[key] = (counts[key] || 0) + 1
    return counts
  }, {})
  const duplicateIndexes = {}

  return (
    <section className="market-page" aria-labelledby="transactions-title">
      <div className="page-heading"><p className="eyebrow">Token Market</p><h1 id="transactions-title">Historial de transacciones</h1><p>Solo se muestran operaciones confirmadas de tu cuenta.</p></div>
      <form className="card market-filters" onSubmit={submit}>
        <div className="market-filters__controls">
          <label className="field" htmlFor="market-transaction-player">
            <span>Buscar jugador por nombre</span>
            <select
                id="market-transaction-player"
                name="playerId"
                value={filters.playerId}
                onChange={update}
                disabled={state.status === 'loading'}
            >
              <option value="">Todos los jugadores</option>
              {playerOptions.map((option) => {
                const key = playerOptionKey(option.name)
                duplicateIndexes[key] = (duplicateIndexes[key] || 0) + 1
                const suffix = duplicateNames[key] > 1 ? ` (${duplicateIndexes[key]})` : ''
                return <option key={option.id} value={option.id}>{option.name}{suffix}</option>
              })}
            </select>
            <small className="muted">Opciones disponibles en las transacciones cargadas.</small>
          </label>
          <label className="field">Tipo<select name="type" value={filters.type} onChange={update}>
            <option value="">Todos</option>
            <option value="PRIMARY_PURCHASE">Compras primarias</option><option value="SALE">Ventas legacy</option><option value="P2P_PURCHASE">Compras P2P</option><option value="P2P_SALE">Ventas P2P</option></select></label>
          <label className="field">Desde<input type="datetime-local" name="from" value={filters.from} onChange={update} /></label>
          <label className="field">Hasta<input type="datetime-local" name="to" value={filters.to} onChange={update} /></label>
        </div>
        <div className="market-filters__actions">
          <button className="button button--primary" type="submit">Aplicar filtros</button>
          <button className="button button--secondary" type="button" onClick={clearFilters} disabled={state.status === 'loading'}>Limpiar filtros</button>
        </div>
      </form>
      {transactions.length === 0
        ? <output className="empty-state">No hay transacciones confirmadas para estos filtros.</output>
        : <div className="market-transactions-list">{transactions.map((transaction) => <article className="card market-transaction-card" data-player-id={transaction.player_id} key={transaction.transaction_id}><div><strong>{operationLabel(transaction.operation_type)}</strong><span>{transactionDate(transaction.confirmed_at)}</span></div><p className="market-transaction-card__snapshot">{transactionPlayerName(transaction)}</p><dl><div><dt>Cantidad</dt><dd>{transaction.quantity}</dd></div><div><dt>Precio unitario</dt><dd>{amount(transaction.unit_price)}</dd></div><div><dt>Total</dt><dd>{amount(transaction.total_amount)}</dd></div><div><dt>Versión cotización</dt><dd>{transaction.quote_version}</dd></div>{showsRealizedPnl(transaction) ? <div><dt>P/L realizada</dt><dd>{amount(transaction.realized_pnl)}</dd></div> : null}</dl></article>)}</div>}
      <MarketTransactionActivityChart jwt={session.jwt} filters={appliedFilters} initialResult={result} />
      <nav className="pagination" aria-label="Paginación del historial"><button className="button button--secondary" type="button" onClick={() => setPage((current) => Math.max(1, current - 1))} disabled={page <= 1}>Anterior</button><span className="pagination__label">Página {pagination.current_page || page} de {pagination.total_pages || 0}</span><button className="button button--secondary" type="button" onClick={() => setPage((current) => current + 1)} disabled={!pagination.total_pages || page >= pagination.total_pages}>Siguiente</button></nav>
    </section>
  )
}
