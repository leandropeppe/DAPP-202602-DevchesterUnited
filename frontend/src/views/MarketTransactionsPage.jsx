import { useCallback, useEffect, useRef, useState } from 'react'

import { getMarketTransactions } from '../api/http.js'
import MarketStateMessage from '../components/MarketStateMessage.jsx'
import MarketTransactionActivityChart from '../components/MarketTransactionActivityChart.jsx'
import MarketTransactionCard from '../components/MarketTransactionCard.jsx'
import { marketPlayerUuid } from '../market/marketState.js'
import { useSession } from '../session/SessionContext.jsx'
import { errorMessage } from './formUtils.js'

const EMPTY_FILTERS = { playerId: '', type: '', from: '', to: '' }
const PAGE_SIZES = [10, 20, 50]
const EMPTY_RESULT = {
  transactions: [],
  pagination: { current_page: 1, page_size: 10, total_pages: 0, total_records: 0 }
}

function dateFilter (value) {
  return value ? new Date(value).toISOString() : undefined
}

function isPagination (value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) &&
    ['current_page', 'page_size', 'total_pages', 'total_records'].every(field => Number.isFinite(value[field]))
}

function isTransactionsResponse (response) {
  const data = response?.data
  return data !== null && typeof data === 'object' && !Array.isArray(data) &&
    Array.isArray(data.transactions) && isPagination(data.pagination)
}

function operationLabel (operationType) {
  if (operationType === 'PRIMARY_PURCHASE' || operationType === 'PURCHASE') return 'Compra primaria'
  if (operationType === 'SALE') return 'Venta legacy'
  if (operationType === 'P2P_PURCHASE') return 'Compra P2P'
  if (operationType === 'P2P_SALE') return 'Venta P2P'
  const operationSuffix = operationType ? `: ${operationType}` : ''
  return `Tipo de operación no reconocido${operationSuffix}`
}

function transactionPlayerName (transaction) {
  const name = transaction?.player_name ?? transaction?.player_name_snapshot
  return typeof name === 'string' && name.trim() ? name.trim() : 'Jugador sin nombre disponible'
}

function playerOptionKey (name) {
  return name.toLocaleLowerCase()
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

export default function MarketTransactionsPage () {
  const session = useSession()
  const [filters, setFilters] = useState({ ...EMPTY_FILTERS })
  const [appliedFilters, setAppliedFilters] = useState({ ...EMPTY_FILTERS })
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [result, setResult] = useState(EMPTY_RESULT)
  const [playerOptions, setPlayerOptions] = useState([])
  const [state, setState] = useState({ status: 'loading', message: '', hasLoaded: false })
  const requestSequence = useRef(0)

  const load = useCallback(async (requestedPage, requestedFilters) => {
    if (!session.jwt || session.isJwtExpired) return

    const sequence = ++requestSequence.current
    setState(current => ({
      ...current,
      status: current.hasLoaded ? 'updating' : 'loading',
      message: ''
    }))

    try {
      const playerId = requestedFilters.playerId
        ? marketPlayerUuid(requestedFilters.playerId)
        : undefined
      const response = await getMarketTransactions({
        jwt: session.jwt,
        filters: {
          playerId: playerId || requestedFilters.playerId || undefined,
          type: requestedFilters.type || undefined,
          from: dateFilter(requestedFilters.from),
          to: dateFilter(requestedFilters.to),
          page: requestedPage,
          limit: pageSize
        }
      })

      if (!isTransactionsResponse(response)) {
        throw new Error('El servidor devolvió un historial incompleto.')
      }
      if (sequence !== requestSequence.current) return

      setResult(response.data)
      setPage(response.data.pagination.current_page)
      setPlayerOptions(current => {
        const next = new Map(current.map(option => [option.id, option]))
        for (const transaction of response.data.transactions) {
          const id = marketPlayerUuid(transaction.player_id)
          if (id) next.set(id, { id, name: transactionPlayerName(transaction) })
        }
        return [...next.values()].sort((left, right) => left.name.localeCompare(right.name, 'es'))
      })
      setState({ status: 'success', message: '', hasLoaded: true })
    } catch (error) {
      if (sequence !== requestSequence.current) return
      setState(current => ({
        status: error?.status === 503 ? 'unavailable' : 'error',
        message: errorMessage(error),
        hasLoaded: current.hasLoaded
      }))
    }
  }, [session.isJwtExpired, session.jwt, pageSize])

  useEffect(() => {
    void load(page, appliedFilters)
  }, [appliedFilters, load, page])

  if (!session.jwt || session.isJwtExpired) {
    return <section className="market-page" aria-labelledby="transactions-title">
      <MarketStateMessage status="error" message="Iniciá sesión para consultar tu historial." />
    </section>
  }

  if (state.status === 'loading' && !state.hasLoaded) {
    return <section className="market-page" aria-labelledby="transactions-title">
      <MarketStateMessage status="loading" />
    </section>
  }

  if ((state.status === 'error' || state.status === 'unavailable') && !state.hasLoaded) {
    return <section className="market-page" aria-labelledby="transactions-title">
      <MarketStateMessage status={state.status} message={state.message} />
    </section>
  }

  const pagination = result.pagination || EMPTY_RESULT.pagination
  const transactions = Array.isArray(result.transactions) ? result.transactions : []
  const currentPage = pagination.current_page || page
  const totalPages = pagination.total_pages || 0
  const pageItems = pageWindow(currentPage, totalPages)
  const effectivePageSize = pagination.page_size || pageSize
  const totalRecords = pagination.total_records || 0
  const firstRecord = totalRecords === 0 ? 0 : ((currentPage - 1) * effectivePageSize) + 1
  const lastRecord = Math.min(currentPage * effectivePageSize, totalRecords)

  function update (event) {
    setFilters(current => ({ ...current, [event.target.name]: event.target.value }))
  }

  function submit (event) {
    event.preventDefault()
    setPage(1)
    setAppliedFilters({ ...filters })
  }

  function clearFilters () {
    setFilters({ ...EMPTY_FILTERS })
    setPage(1)
    setAppliedFilters({ ...EMPTY_FILTERS })
  }

  const duplicateNames = playerOptions.reduce((counts, option) => {
    const key = playerOptionKey(option.name)
    counts[key] = (counts[key] || 0) + 1
    return counts
  }, {})
  const duplicateIndexes = {}
  const updating = state.status === 'updating'

  return (
    <section className="market-page" aria-labelledby="transactions-title">
      <div className="page-heading">
        <p className="eyebrow">Token Market</p>
        <h1 id="transactions-title">Historial de transacciones</h1>
        <p>Solo se muestran operaciones confirmadas de tu cuenta.</p>
      </div>

      <form className="card market-filters" onSubmit={submit}>
        <div className="market-filters__controls market-transaction-filters__controls">
          <label className="field" htmlFor="market-transaction-player">
            <span>Buscar jugador por nombre</span>
            <select id="market-transaction-player" name="playerId" value={filters.playerId} onChange={update} disabled={updating}>
              <option value="">Todos los jugadores</option>
              {playerOptions.map(option => {
                const key = playerOptionKey(option.name)
                duplicateIndexes[key] = (duplicateIndexes[key] || 0) + 1
                const suffix = duplicateNames[key] > 1 ? ` (${duplicateIndexes[key]})` : ''
                return <option key={option.id} value={option.id}>{option.name}{suffix}</option>
              })}
            </select>
          </label>
          <label className="field"><span>Tipo</span><select name="type" value={filters.type} onChange={update} disabled={updating}>
            <option value="">Todos</option>
            <option value="PRIMARY_PURCHASE">Compras primarias</option>
            <option value="PURCHASE">Compras</option>
            <option value="SALE">Ventas legacy</option>
            <option value="P2P_PURCHASE">Compras P2P</option>
            <option value="P2P_SALE">Ventas P2P</option>
          </select></label>
          <label className="field"><span>Desde</span><input type="datetime-local" name="from" value={filters.from} onChange={update} disabled={updating} /></label>
          <label className="field"><span>Hasta</span><input type="datetime-local" name="to" value={filters.to} onChange={update} disabled={updating} /></label>
        </div>
        <div className="market-filters__actions">
          <button className="button button--primary" type="submit" disabled={updating}>Aplicar filtros</button>
          <button className="button button--secondary" type="button" onClick={clearFilters} disabled={updating}>Limpiar filtros</button>
        </div>
      </form>

      {updating ? <p className="market-transactions__updating" role="status" aria-live="polite">Actualizando transacciones…</p> : null}
      {state.status === 'error' || state.status === 'unavailable'
        ? <p className="market-transactions__update-error" role="alert">{state.message} La información anterior sigue disponible; podés reintentar cambiando de página o aplicando nuevamente los filtros.</p>
        : null}

      <div className={`market-transactions-list${updating ? ' market-transactions-list--updating' : ''}`}>
        {transactions.length === 0
          ? <output className="empty-state">No hay transacciones confirmadas para estos filtros.</output>
          : transactions.map(transaction => <MarketTransactionCard key={transaction.transaction_id} transaction={transaction} operationLabel={operationLabel(transaction.operation_type)} />)}
      </div>

      <nav className="pagination market-transactions-pagination" aria-label="Paginación del historial">
        <div className="market-transactions-pagination__summary">
          <span>Mostrando {firstRecord}–{lastRecord} de {totalRecords} transacciones</span>
          <label className="market-transactions-pagination__size">
            <span>Transacciones por página</span>
            <select aria-label="Transacciones por página" value={pageSize} onChange={event => { setPageSize(Number(event.target.value)); setPage(1) }} disabled={updating}>
              {PAGE_SIZES.map(size => <option key={size} value={size}>{size}</option>)}
            </select>
          </label>
        </div>
        <div className="market-transactions-pagination__controls">
          <button className="button button--secondary" type="button" onClick={() => setPage(current => Math.max(1, current - 1))} disabled={updating || currentPage <= 1}>Anterior</button>
          <div className="market-transactions-pagination__pages" aria-label="Páginas">
            {pageItems.map((item, index) => item === 'ellipsis'
              ? <span key={`ellipsis-${index}`} aria-hidden="true">…</span>
              : <button key={item} className={`button ${item === currentPage ? 'button--primary' : 'button--secondary'}`} type="button" aria-current={item === currentPage ? 'page' : undefined} onClick={() => setPage(item)} disabled={updating || item === currentPage}>{item}</button>)}
          </div>
          <button className="button button--secondary" type="button" onClick={() => setPage(current => Math.min(totalPages, current + 1))} disabled={updating || !totalPages || currentPage >= totalPages}>Siguiente</button>
        </div>
      </nav>

      <MarketTransactionActivityChart jwt={session.jwt} filters={appliedFilters} />
    </section>
  )
}
