import { useEffect, useMemo, useRef, useState } from 'react'
import {
  getValuationSimulatorConfig,
  simulatePlayerValuation
} from '../api/http.js'
import MaradonaLoader, { remainingLoadingTime } from './MaradonaLoader.jsx'
import { errorMessage } from '../views/formUtils.js'

function numberValue (value) {
  return value === null || value === undefined || value === '' ? null : Number(value)
}

function formatValue (value) {
  if (value === null || value === undefined || value === '') return 'No disponible'
  if (typeof value === 'number') return value.toLocaleString('es-AR', { maximumFractionDigits: 2 })
  return String(value)
}

function initialValues (statistics) {
  return Object.fromEntries((statistics || []).filter((stat) => stat.editable).map((stat) => [
    stat.metric,
    stat.kind === 'ACCUMULATIVE' ? 0 : (stat.currentValue ?? '')
  ]))
}

function projectedValue (stat, value) {
  const current = numberValue(stat.currentValue)
  const edited = numberValue(value)
  if (stat.kind === 'ACCUMULATIVE') {
    return current === null || edited === null ? null : current + edited
  }
  return edited
}

function friendlyBlockingReason (reason) {
  const messages = {
    DATOS_INSUFICIENTES: 'No hay estadísticas suficientes para preparar la simulación.',
    DATOS_INSUFICIENTES_APPS_MINUTES: 'Faltan partidos o minutos actuales para proyectar.',
    ESTRATEGIA_AUSENTE: 'No hay una estrategia de valorización disponible.',
    POSICION_NO_SOPORTADA: 'La posición del jugador no está soportada por la estrategia vigente.',
    POSICION_AMBIGUA: 'La posición del jugador es ambigua y no puede resolverse automáticamente.',
    METRICA_POSITIVA_AUSENTE: 'Falta una métrica requerida por la estrategia.',
    COTIZACION_ACTUAL_AUSENTE: 'El jugador todavía no tiene una cotización actual.'
  }
  const key = String(reason).split(':')[0]
  return messages[key] || `La simulación está bloqueada: ${String(reason).toLowerCase()}.`
}

function friendlyDifferenceReason (reason) {
  if (reason === 'COTIZACION_ACTUAL_AUSENTE') return 'Cotización actual no disponible'
  if (reason === 'COTIZACION_ACTUAL_CERO') return 'No se puede calcular sobre una cotización cero'
  return reason || 'No disponible'
}

function differenceClass (value) {
  if (value > 0) return 'player-simulator__difference--positive'
  if (value < 0) return 'player-simulator__difference--negative'
  return 'player-simulator__difference--zero'
}

export default function PlayerValuationSimulator ({ playerId, onClose }) {
  const [status, setStatus] = useState('loading')
  const [config, setConfig] = useState(null)
  const [error, setError] = useState('')
  const [horizon, setHorizon] = useState(null)
  const [values, setValues] = useState({})
  const [result, setResult] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const timerRef = useRef(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    void getValuationSimulatorConfig(playerId)
      .then((response) => {
        if (!mountedRef.current) return
        const next = response?.data || {}
        const horizons = Array.isArray(next.horizons) ? next.horizons : []
        setConfig(next)
        setHorizon(horizons[0] ?? null)
        setValues(initialValues(next.statistics))
        setStatus('ready')
      })
      .catch((reason) => {
        if (!mountedRef.current) return
        setError(errorMessage(reason))
        setStatus('error')
      })
    return () => {
      mountedRef.current = false
      if (timerRef.current) window.clearTimeout(timerRef.current)
    }
  }, [playerId])

  const editable = useMemo(() => (config?.statistics || []).filter((stat) => stat.editable), [config])
  const blocked = (config?.blockingReasons || []).length > 0

  function resetScenario () {
    setHorizon(config?.horizons?.[0] ?? null)
    setValues(initialValues(config?.statistics))
    setResult(null)
    setError('')
  }

  function modifications () {
    return Object.fromEntries(editable.filter((stat) => {
      const current = values[stat.metric]
      return stat.kind === 'ACCUMULATIVE'
        ? numberValue(current) !== null && numberValue(current) !== 0
        : numberValue(current) !== numberValue(stat.currentValue)
    }).map((stat) => [stat.metric, numberValue(values[stat.metric])]))
  }

  async function submit () {
    setSubmitting(true)
    setError('')
    const started = Date.now()
    try {
      const response = await simulatePlayerValuation(playerId, {
        horizonMatches: horizon,
        modifications: modifications()
      })
      const remaining = remainingLoadingTime(started)
      await new Promise((resolve) => {
        timerRef.current = window.setTimeout(resolve, remaining)
      })
      if (!mountedRef.current) return
      setResult(response?.data || null)
    } catch (reason) {
      if (mountedRef.current) setError(errorMessage(reason))
    } finally {
      if (mountedRef.current) setSubmitting(false)
    }
  }

  if (status === 'loading') {
    return <div className="player-simulator__backdrop"><section className="player-simulator" role="dialog" aria-modal="true" aria-labelledby="simulator-title"><MaradonaLoader /></section></div>
  }

  return (
    <div className="player-simulator__backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="player-simulator" role="dialog" aria-modal="true" aria-labelledby="simulator-title">
        <header className="player-simulator__header">
          <div>
            <p className="eyebrow">Simulador</p>
            <h2 id="simulator-title">Simular cotización</h2>
            {config?.resolvedPosition ? <p>{config.resolvedPosition} · {config.strategyId} v{config.strategyVersion}</p> : null}
          </div>
          <button className="player-simulator__close" type="button" onClick={onClose} aria-label="Cerrar simulador">×</button>
        </header>

        {error ? <p className="player-simulator__message player-simulator__message--error" role="alert">{error}</p> : null}
        {blocked ? <div className="player-simulator__message player-simulator__message--warning" role="alert"><strong>No se puede simular todavía.</strong><ul>{config.blockingReasons.map((reason) => <li key={reason}>{friendlyBlockingReason(reason)}</li>)}</ul></div> : null}

        <div className="player-simulator__controls">
          <label htmlFor="simulator-horizon">Horizonte</label>
          <select id="simulator-horizon" value={horizon ?? ''} onChange={(event) => setHorizon(Number(event.target.value))} disabled={submitting}>
            {(config?.horizons || []).map((option) => <option key={option} value={option}>{option} partidos</option>)}
          </select>
          <div className="player-simulator__facts">
            <span>Partidos: <strong>{formatValue(config?.currentApps)} → {formatValue(numberValue(config?.currentApps) === null || horizon === null ? null : config.currentApps + horizon)}</strong></span>
            <span>Minutos: <strong>{formatValue(config?.currentMinutes)} → {formatValue(numberValue(config?.currentMinutes) === null || horizon === null ? null : config.currentMinutes + horizon * 90)}</strong></span>
          </div>
        </div>

        <div className="player-simulator__stats" aria-label="Estadísticas del escenario">
          {editable.map((stat) => {
            const projected = projectedValue(stat, values[stat.metric])
            const isAccum = stat.kind === 'ACCUMULATIVE'
            return (
              <div className="player-simulator__stat" key={stat.metric}>
                <div className="player-simulator__stat-heading"><strong>{stat.label}</strong><span>{stat.kind}</span></div>
                <div className="player-simulator__stat-values">
                  <span><small>Actual</small>{formatValue(stat.currentValue)}</span>
                  <label><small>{isAccum ? 'Predicción' : 'Proyectado'}</small><input aria-label={`${stat.label} ${isAccum ? 'predicción' : 'valor proyectado'}`} type="number" min={stat.min ?? undefined} max={stat.max ?? undefined} step="any" value={values[stat.metric] ?? ''} onChange={(event) => setValues((current) => ({ ...current, [stat.metric]: event.target.value }))} disabled={submitting} /></label>
                  <span><small>Proyectado</small>{formatValue(projected)}</span>
                </div>
              </div>
            )
          })}
        </div>

        {submitting ? <MaradonaLoader /> : null}
        {result ? <section className="player-simulator__result" aria-live="polite"><h3>Resultado</h3><div className="player-simulator__result-grid"><span>Cotización actual<strong>{formatValue(result.currentQuote)}</strong></span><span>Cotización simulada<strong>{formatValue(result.simulatedQuote)}</strong></span><span className={differenceClass(Number(result.monetaryDifference))}>Diferencia monetaria<strong>{formatValue(result.monetaryDifference)}</strong></span><span className={differenceClass(Number(result.percentageDifference))}>Diferencia porcentual<strong>{result.percentageDifference === null ? friendlyDifferenceReason(result.percentageDifferenceUnavailableReason) : `${formatValue(result.percentageDifference)} %`}</strong></span></div></section> : null}

        <footer className="player-simulator__actions"><button className="button button--secondary" type="button" onClick={resetScenario} disabled={submitting}>Restablecer</button><button className="button button--primary" type="button" onClick={() => { void submit() }} disabled={submitting || blocked || horizon === null}>{submitting ? 'Calculando…' : 'Simular'}</button></footer>
      </section>
    </div>
  )
}
