import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts'

const CHART_COLORS = ['#d71920', '#1d4ed8', '#047857', '#b45309', '#7c3aed', '#0f766e', '#be123c', '#374151']

function numericValue (value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function playerLabel (position) {
  return typeof position?.player_name === 'string' && position.player_name.trim()
    ? position.player_name.trim()
    : 'Jugador sin nombre disponible'
}

function quoteStatusLabel (status) {
  if (status === 'CURRENT') return 'Cotización actual'
  if (status === 'HISTORICAL') return 'Cotización histórica'
  if (status === 'ABSENT') return 'Sin cotización'
  return 'Valoración no disponible'
}

export function formatChartAmount (value) {
  return numericValue(value) === null
    ? 'No disponible'
    : numericValue(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function buildPortfolioChartData (positions = []) {
  const distributionCandidates = []
  const comparisonData = []
  const excludedPositions = []

  for (const position of positions) {
    const currentValue = numericValue(position.current_value)
    const pendingCost = numericValue(position.pending_acquisition_cost)
    const status = position.quote_availability
    const item = {
      playerId: position.player_id,
      name: playerLabel(position),
      currentValue,
      pendingCost,
      quoteStatus: quoteStatusLabel(status)
    }

    if (status !== 'CURRENT' || currentValue === null || currentValue < 0) {
      excludedPositions.push({ ...item, reason: status === 'CURRENT' ? 'Valor actual no disponible' : quoteStatusLabel(status) })
      continue
    }

    distributionCandidates.push(item)
    if (pendingCost !== null) comparisonData.push(item)
    else excludedPositions.push({ ...item, reason: 'Costo pendiente no disponible' })
  }

  const totalValue = distributionCandidates.reduce((total, item) => total + item.currentValue, 0)
  const distributionData = totalValue > 0
    ? distributionCandidates.map((item) => ({ ...item, value: item.currentValue, percentage: (item.currentValue / totalValue) * 100 }))
    : []

  return { distributionData, comparisonData, excludedPositions, totalValue }
}

function ChartState ({ children }) {
  return <p className="market-chart__state" role="status">{children}</p>
}

function PortfolioDistribution ({ data, totalValue }) {
  return (
    <article className="card market-chart-card" aria-labelledby="portfolio-distribution-title">
      <h3 id="portfolio-distribution-title">Distribución del portfolio</h3>
      {data.length === 0
        ? <ChartState>No hay valores actuales suficientes para representar una distribución.</ChartState>
        : (
          <>
            <div className="market-chart__visual" role="img" aria-label="Distribución del valor actual del portfolio por jugador">
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie data={data.map((entry, index) => ({ ...entry, fill: CHART_COLORS[index % CHART_COLORS.length] }))} dataKey="value" nameKey="name" innerRadius="55%" outerRadius="80%" paddingAngle={2} />
                  <Tooltip formatter={(value) => formatChartAmount(value)} />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <p className="market-chart__summary">Valor representado: {formatChartAmount(totalValue)}</p>
            <table className="market-chart-table">
              <caption>Detalle de distribución por jugador</caption>
              <thead><tr><th scope="col">Jugador</th><th scope="col">Valor actual</th><th scope="col">Porcentaje</th></tr></thead>
              <tbody>{data.map((item) => <tr key={item.playerId}><th scope="row">{item.name}</th><td>{formatChartAmount(item.value)}</td><td>{item.percentage.toLocaleString('es-AR', { maximumFractionDigits: 2 })}%</td></tr>)}</tbody>
            </table>
          </>
        )}
    </article>
  )
}

function PortfolioComparison ({ data }) {
  return (
    <article className="card market-chart-card" aria-labelledby="portfolio-comparison-title">
      <h3 id="portfolio-comparison-title">Costo pendiente vs. valor actual</h3>
      {data.length === 0
        ? <ChartState>No hay posiciones con costo y valor actual comparables.</ChartState>
        : (
          <>
            <div className="market-chart__visual" role="img" aria-label="Comparación de costo pendiente y valor actual por jugador">
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={data} margin={{ top: 8, right: 12, left: 8, bottom: 40 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" angle={-25} textAnchor="end" interval={0} height={60} />
                  <YAxis tickFormatter={(value) => formatChartAmount(value)} />
                  <Tooltip formatter={(value) => formatChartAmount(value)} />
                  <Legend />
                  <Bar dataKey="pendingCost" name="Costo pendiente" fill="#1d4ed8" />
                  <Bar dataKey="currentValue" name="Valor actual" fill="#047857" />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <table className="market-chart-table">
              <caption>Detalle de costo pendiente y valor actual</caption>
              <thead><tr><th scope="col">Jugador</th><th scope="col">Costo pendiente</th><th scope="col">Valor actual</th></tr></thead>
              <tbody>{data.map((item) => <tr key={item.playerId}><th scope="row">{item.name}</th><td>{formatChartAmount(item.pendingCost)}</td><td>{formatChartAmount(item.currentValue)}</td></tr>)}</tbody>
            </table>
          </>
        )}
    </article>
  )
}

export default function MarketPortfolioCharts ({ positions = [] }) {
  const { distributionData, comparisonData, excludedPositions, totalValue } = buildPortfolioChartData(positions)
  return (
    <section className="market-charts" aria-labelledby="portfolio-charts-title">
      <div className="market-charts__heading"><h2 id="portfolio-charts-title">Visualizaciones del portfolio</h2><p>Solo se representan posiciones con datos de valoración actuales válidos.</p></div>
      <div className="market-charts__grid">
        <PortfolioDistribution data={distributionData} totalValue={totalValue} />
        <PortfolioComparison data={comparisonData} />
      </div>
      {excludedPositions.length > 0
        ? (
          <div className="market-chart__exclusions">
            <ChartState>{excludedPositions.length} posición{excludedPositions.length === 1 ? '' : 'es'} excluida{excludedPositions.length === 1 ? '' : 's'} por falta de valoración actual o costo válido.</ChartState>
            <ul>{excludedPositions.map((item) => <li key={`${item.playerId}-${item.reason}`}>{item.name}: {item.reason}</li>)}</ul>
          </div>
        )
        : null}
    </section>
  )
}
