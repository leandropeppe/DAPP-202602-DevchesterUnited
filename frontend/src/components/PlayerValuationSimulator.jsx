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

function formatSigned (value) {
  const number = numberValue(value)
  if (number === null || !Number.isFinite(number)) return 'No disponible'
  if (number === 0) return formatValue(number)
  return `${number > 0 ? '+' : ''}${formatValue(number)}`
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

function tokenMoney (tokens, quote) {
  const amount = numberValue(tokens)
  const price = numberValue(quote)
  if (amount === null || price === null || !Number.isFinite(amount) || !Number.isFinite(price)) return null
  return amount * price
}

export default function PlayerValuationSimulator ({ playerId, jwt, onClose }) {
  const [status, setStatus] = useState('loading')
  const [config, setConfig] = useState(null)
  const [error, setError] = useState('')
  const [horizon, setHorizon] = useState(null)
  const [tokens, setTokens] = useState('1')
  const [values, setValues] = useState({})
  const [result, setResult] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const timerRef = useRef(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    const configRequest = jwt === undefined
      ? getValuationSimulatorConfig(playerId)
      : getValuationSimulatorConfig(playerId, jwt)

    void configRequest
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
  }, [playerId, jwt])

  const editable = useMemo(() => (config?.statistics || []).filter((stat) => stat.editable), [config])
  const blocked = (config?.blockingReasons || []).length > 0
  const tokensNumber = numberValue(tokens)
  const tokensInvalid = tokens === '' || tokensNumber === null || !Number.isFinite(tokensNumber) || tokensNumber <= 0

  function resetScenario () {
    setHorizon(config?.horizons?.[0] ?? null)
    setTokens('1')
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
    if (tokensInvalid) {
      setError(
          'Ingresá una cantidad de tokens positiva para calcular el impacto económico.'
      )
      return
    }

    setSubmitting(true)
    setError('')

    const started = Date.now()

    try {
      const simulationRequest = {
        horizonMatches: horizon,
        modifications: modifications()
      }

      const response = jwt === undefined
          ? await simulatePlayerValuation(
              playerId,
              simulationRequest
          )
          : await simulatePlayerValuation(
              playerId,
              simulationRequest,
              jwt
          )

      const elapsed = Date.now() - started
      const remaining = remainingLoadingTime(elapsed)

      await new Promise((resolve) => {
        timerRef.current = window.setTimeout(
            resolve,
            remaining
        )
      })

      if (!mountedRef.current) return

      setResult(response?.data || null)
    } catch (reason) {
      if (mountedRef.current) {
        setError(errorMessage(reason))
      }
    } finally {
      if (mountedRef.current) {
        setSubmitting(false)
      }
    }
  }

  function renderLoader () {
    return <div className="player-simulator__loader-overlay" aria-busy="true"><MaradonaLoader /></div>
  }

  function renderResult () {
    const actualValue = tokenMoney(tokens, result?.currentQuote)
    const projectedValueForTokens = tokenMoney(tokens, result?.simulatedQuote)
    const tokenDifference = actualValue === null || projectedValueForTokens === null
      ? null
      : projectedValueForTokens - actualValue

    return (
      <div className="player-simulator__result-view" aria-live="polite">
        <p className="eyebrow">Resultado de la simulación</p>
        <h3>Cotización proyectada</h3>
        <strong className="player-simulator__result-hero">{formatValue(result?.simulatedQuote)}</strong>
        <p className={`player-simulator__result-variation ${differenceClass(Number(result?.percentageDifference))}`}>
          Variación: <strong>{result?.percentageDifference === null ? friendlyDifferenceReason(result?.percentageDifferenceUnavailableReason) : `${formatSigned(result?.percentageDifference)} %`}</strong>
        </p>

        <div className="player-simulator__result-grid">
          <span>Cotización actual<strong>{formatValue(result?.currentQuote)}</strong></span>
          <span>Cotización proyectada<strong>{formatValue(result?.simulatedQuote)}</strong></span>
          <span className={differenceClass(Number(result?.monetaryDifference))}>Diferencia absoluta<strong>{formatSigned(result?.monetaryDifference)}</strong></span>
          <span className={differenceClass(Number(result?.percentageDifference))}>Diferencia porcentual<strong>{result?.percentageDifference === null ? friendlyDifferenceReason(result?.percentageDifferenceUnavailableReason) : `${formatSigned(result?.percentageDifference)} %`}</strong></span>
        </div>

        <section className="player-simulator__token-impact" aria-label="Impacto económico de los tokens">
          <h4>Impacto por cantidad de tokens</h4>
          <div className="player-simulator__result-grid">
            <span>Cantidad de tokens<strong>{formatValue(tokensNumber)}</strong></span>
            <span>Valor actual<strong>{actualValue === null ? 'No disponible' : formatValue(actualValue)}</strong></span>
            <span>Valor proyectado<strong>{projectedValueForTokens === null ? 'No disponible' : formatValue(projectedValueForTokens)}</strong></span>
            <span className={differenceClass(Number(tokenDifference))}>Ganancia/pérdida potencial<strong>{tokenDifference === null ? 'No disponible' : formatSigned(tokenDifference)}</strong></span>
          </div>
        </section>
      </div>
    )
  }

  function renderForm () {
    return (
      <>
        {error ? <p className="player-simulator__message player-simulator__message--error" role="alert">{error}</p> : null}
        {blocked ? <div className="player-simulator__message player-simulator__message--warning" role="alert"><strong>No se puede simular todavía.</strong><ul>{config.blockingReasons.map((reason) => <li key={reason}>{friendlyBlockingReason(reason)}</li>)}</ul></div> : null}

        <div className="player-simulator__controls">
          <div>
            <label htmlFor="simulator-horizon">Horizonte</label>
            <select id="simulator-horizon" value={horizon ?? ''} onChange={(event) => setHorizon(Number(event.target.value))} disabled={submitting}>
              {(config?.horizons || []).map((option) => <option key={option} value={option}>{option} partidos</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="simulator-tokens">Cantidad de tokens</label>
            <input id="simulator-tokens" type="number" min="0.01" step="any" value={tokens} onChange={(event) => setTokens(event.target.value)} disabled={submitting} aria-invalid={tokensInvalid} />
          </div>
          <div className="player-simulator__facts">
            <span>Partidos: <strong>{formatValue(config?.currentApps)} → {formatValue(numberValue(config?.currentApps) === null || horizon === null ? null : config.currentApps + horizon)}</strong></span>
            <span>Minutos: <strong>{formatValue(config?.currentMinutes)} → {formatValue(numberValue(config?.currentMinutes) === null || horizon === null ? null : config.currentMinutes + horizon * 90)}</strong></span>
          </div>
        </div>

        <div className="player-simulator__stats" role="table" aria-label="Estadísticas del escenario">
          <div className="player-simulator__stat-row player-simulator__stat-row--heading" role="row">
            <strong role="columnheader">Métrica</strong><strong role="columnheader">Actual</strong><strong role="columnheader">Predicción</strong><strong role="columnheader">Proyectado</strong>
          </div>
          {editable.map((stat) => {
            const projected = projectedValue(stat, values[stat.metric])
            const isAccum = stat.kind === 'ACCUMULATIVE'
            return (
              <div className="player-simulator__stat-row" key={stat.metric} role="row">
                <span role="cell"><strong>{stat.label}</strong><small>{stat.kind}</small></span>
                <span role="cell">{formatValue(stat.currentValue)}</span>
                <label role="cell" htmlFor={`simulator-${stat.metric}`}><input id={`simulator-${stat.metric}`} aria-label={`${stat.label} ${isAccum ? 'predicción' : 'valor proyectado'}`} type="number" min={stat.min ?? undefined} max={stat.max ?? undefined} step="any" value={values[stat.metric] ?? ''} onChange={(event) => setValues((current) => ({ ...current, [stat.metric]: event.target.value }))} disabled={submitting} /></label>
                <span role="cell">{formatValue(projected)}</span>
              </div>
            )
          })}
        </div>

        <footer className="player-simulator__actions"><button className="button button--secondary" type="button" onClick={resetScenario} disabled={submitting}>Restablecer</button><button className="button button--primary" type="button" onClick={() => { void submit() }} disabled={submitting || blocked || horizon === null || tokensInvalid}>{submitting ? 'Calculando…' : 'Simular'}</button></footer>
      </>
    )
  }

  if (status === 'loading') {
    return <dialog open className="player-simulator__backdrop" aria-modal="true" aria-labelledby="simulator-title"><div className="player-simulator"><MaradonaLoader /></div></dialog>
  }

  return (
    <dialog open className="player-simulator__backdrop" aria-modal="true" aria-labelledby="simulator-title" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <div className="player-simulator">
        <header className="player-simulator__header">
          <div>
            <p className="eyebrow">Simulador</p>
            <h2 id="simulator-title">Simular cotización</h2>
            {config?.resolvedPosition ? <p>{config.resolvedPosition} · {config.strategyId} v{config.strategyVersion}</p> : null}
          </div>
          <button className="player-simulator__close" type="button" onClick={onClose} aria-label="Cerrar simulador">×</button>
        </header>

        {result ? renderResult() : renderForm()}
        {result ? <footer className="player-simulator__actions"><button className="button button--secondary" type="button" onClick={resetScenario}>Restablecer</button><button className="button button--secondary" type="button" onClick={() => { setResult(null); setError('') }}>Modificar escenario</button><button className="button button--primary" type="button" onClick={onClose}>Cerrar</button></footer> : null}
        {submitting ? renderLoader() : null}
      </div>
    </dialog>
  )
}
