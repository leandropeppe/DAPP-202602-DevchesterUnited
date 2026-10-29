import { useEffect, useState } from 'react'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMarketTransactions } from '../api/http.js'
import { marketPlayerUuid } from '../market/marketState.js'

const MAX_ACTIVITY_PAGES = 20
const ACTIVITY_PAGE_SIZE = 100

function dateFilter (value) {
  return value ? new Date(value).toISOString() : undefined
}

function isPagination (value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) && ['current_page', 'page_size', 'total_pages', 'total_records'].every((field) => Number.isFinite(value[field]))
}

function validResponse (response) {
  return response?.data && Array.isArray(response.data.transactions) && isPagination(response.data.pagination)
}

function localDay (value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const parts = new Intl.DateTimeFormat('en', { year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(date)
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

function displayDay (key) {
  const [year, month, day] = key.split('-')
  return `${day}/${month}/${year}`
}

export function aggregateTransactionActivity (transactions = []) {
  const grouped = new Map()
  for (const transaction of transactions) {
    const day = localDay(transaction.confirmed_at)
    const quantity = Number(transaction.quantity)
    if (!day || !Number.isInteger(quantity) || quantity < 0) continue
    const current = grouped.get(day) || { day, purchases: 0, sales: 0 }
    if (transaction.operation_type === 'PURCHASE') current.purchases += quantity
    if (transaction.operation_type === 'SALE') current.sales += quantity
    grouped.set(day, current)
  }
  return [...grouped.values()].sort((left, right) => left.day.localeCompare(right.day)).map((item) => ({ ...item, label: displayDay(item.day) }))
}

function formatQuantity (value) {
  return Number(value).toLocaleString('es-AR')
}

export default function MarketTransactionActivityChart ({ jwt, filters, initialResult }) {
  const [state, setState] = useState({ status: 'loading', rows: [], partial: false, totalPages: 0 })
  const completeHistorySuffix = state.totalPages === 1 ? '' : 's'
  let historyMessage = null
  if (state.status === 'success') {
    historyMessage = state.partial
      ? <p className="market-chart__state">Se muestran las primeras {MAX_ACTIVITY_PAGES} de {state.totalPages} páginas; la actividad es parcial y no representa todo el historial.</p>
      : <p className="market-chart__state">Historial completo cargado: {state.totalPages} página{completeHistorySuffix}.</p>
  }

  useEffect(() => {
    let cancelled = false
    async function load () {
      setState({ status: 'loading', rows: [], partial: false, totalPages: 0 })
      try {
        const requestFilters = {
          playerId: filters.playerId ? marketPlayerUuid(filters.playerId) || filters.playerId : undefined,
          type: filters.type || undefined,
          from: dateFilter(filters.from),
          to: dateFilter(filters.to)
        }
        const seededPage = initialResult?.pagination?.current_page === 1 && Array.isArray(initialResult.transactions)
        let transactions = seededPage ? [...initialResult.transactions] : []
        let totalPages = seededPage ? initialResult.pagination.total_pages : 0
        if (!seededPage) {
          const firstResponse = await getMarketTransactions({ jwt, filters: { ...requestFilters, page: 1, limit: ACTIVITY_PAGE_SIZE } })
          if (!validResponse(firstResponse)) throw new Error('El servidor devolvió un historial incompleto.')
          transactions = [...firstResponse.data.transactions]
          totalPages = firstResponse.data.pagination.total_pages
        }
        const pagesToLoad = Math.min(totalPages, MAX_ACTIVITY_PAGES)
        const pageResponses = await Promise.all(Array.from(
          { length: Math.max(0, pagesToLoad - 1) },
          (_, index) => getMarketTransactions({ jwt, filters: { ...requestFilters, page: index + 2, limit: ACTIVITY_PAGE_SIZE } })
        ))
        for (const response of pageResponses) {
          if (!validResponse(response)) throw new Error('El servidor devolvió un historial incompleto.')
          transactions.push(...response.data.transactions)
        }
        if (cancelled) return
        setState({ status: 'success', rows: aggregateTransactionActivity(transactions), partial: totalPages > MAX_ACTIVITY_PAGES, totalPages })
      } catch (error) {
        if (!cancelled) setState({ status: 'error', message: error?.message || 'No fue posible cargar la actividad.', rows: [], partial: false, totalPages: 0 })
      }
    }
    if (jwt) void load()
    return () => { cancelled = true }
  }, [filters.from, filters.playerId, filters.to, filters.type, initialResult, jwt])

  return (
    <section className="card market-chart-card market-activity-chart" aria-labelledby="transaction-activity-title">
      <h2 id="transaction-activity-title">Evolución de compras y ventas</h2>
      <p>Tokens confirmados por día, según la zona horaria local del navegador.</p>
      {state.status === 'loading' ? <output className="market-chart__state" aria-live="polite">Cargando actividad…</output> : null}
      {state.status === 'error' ? <p className="market-chart__state status-message--error" role="alert">{state.message}</p> : null}
      {state.status === 'success' && state.rows.length === 0 ? <output className="market-chart__state" aria-live="polite">No hay operaciones confirmadas para representar.</output> : null}
      {state.status === 'success' && state.rows.length > 0
        ? (
          <>
            <div className="market-chart__visual" role="img" aria-label="Evolución diaria de tokens comprados y vendidos">
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={state.rows} margin={{ top: 8, right: 12, left: 8, bottom: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="label" />
                  <YAxis tickFormatter={formatQuantity} allowDecimals={false} />
                  <Tooltip formatter={(value) => formatQuantity(value)} />
                  <Legend />
                  <Line type="monotone" dataKey="purchases" name="Compras" stroke="#1d4ed8" strokeWidth={2} />
                  <Line type="monotone" dataKey="sales" name="Ventas" stroke="#d71920" strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            <table className="market-chart-table">
              <caption>Actividad diaria de tokens confirmados</caption>
              <thead><tr><th scope="col">Día</th><th scope="col">Compras</th><th scope="col">Ventas</th></tr></thead>
              <tbody>{state.rows.map((row) => <tr key={row.day}><th scope="row">{row.label}</th><td>{formatQuantity(row.purchases)}</td><td>{formatQuantity(row.sales)}</td></tr>)}</tbody>
            </table>
          </>
        )
        : null}
      {historyMessage}
    </section>
  )
}
