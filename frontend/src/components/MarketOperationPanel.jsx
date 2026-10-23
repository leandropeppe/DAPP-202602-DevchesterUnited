import { useRef, useState } from 'react'
import { getMarketOperation, purchaseMarketTokens, sellMarketTokens } from '../api/http.js'
import OperationStatus from './OperationStatus.jsx'
import { isMarketPlayerOperable, marketPlayerUuid } from '../market/marketState.js'
import { errorMessage } from '../views/formUtils.js'

function formatAmount (value) {
  if (value === null || value === undefined || value === '') return 'Sin información'
  return Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function newIdempotencyKey () {
  return globalThis.crypto?.randomUUID?.() || `market-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const MAX_MARKET_QUANTITY = 100

function quantityValidationMessage (value, maxQuantity = MAX_MARKET_QUANTITY) {
  if (value === '') return 'Ingresá una cantidad de tokens.'
  const parsed = Number(value)
  if (!Number.isInteger(parsed)) return 'La cantidad debe ser un número entero.'
  if (parsed < 1) return 'La cantidad debe ser al menos 1 token.'
  if (parsed > maxQuantity) return `La cantidad no puede superar ${maxQuantity} tokens.`
  return ''
}

function isObject (value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function operationResponse (response) {
  const data = response?.data
  if (!isObject(data) || typeof data.status !== 'string' || !data.status.trim() || typeof data.updated_at !== 'string' || !data.updated_at) {
    const error = new Error('El servidor devolvió una respuesta incompleta para la operación.')
    error.incompleteData = {}
    throw error
  }
  return data
}

function operationLookupResponse (response) {
  const data = response?.data
  if (!isObject(data) || typeof data.idempotency_key !== 'string' || !data.idempotency_key.trim() || typeof data.operation_type !== 'string' || !data.operation_type.trim() || typeof data.status !== 'string' || !data.status.trim() || typeof data.updated_at !== 'string' || !data.updated_at) {
    throw new Error('El servidor devolvió un estado incompleto para la operación.')
  }
  return data
}

export default function MarketOperationPanel ({ player, type, jwt, onClose, panelRef, maxQuantity = MAX_MARKET_QUANTITY, availableQuantity, onConfirmed }) {
  const [quantity, setQuantity] = useState('1')
  const [confirmed, setConfirmed] = useState(false)
  const [state, setState] = useState({ status: 'idle', message: '' })
  const [result, setResult] = useState(null)
  const [needsRecovery, setNeedsRecovery] = useState(false)
  const [idempotencyKey] = useState(newIdempotencyKey)
  const confirmedNotified = useRef(false)

  const playerId = marketPlayerUuid(player.id)
  const canOperate = isMarketPlayerOperable(player) && playerId && player.quote_id && player.quote_version && player.price_per_token !== null && player.price_per_token !== undefined
  const quantityNumber = Number(quantity)
  const quantityLimit = Math.min(MAX_MARKET_QUANTITY, Math.max(0, Number(maxQuantity) || 0))
  const quantityError = quantityValidationMessage(quantity, quantityLimit)
  const quantityValid = quantityError === ''
  const estimatedTotal = Number(player.price_per_token) * quantityNumber
  const isSale = type === 'SALE'
  const operationLabel = type === 'PURCHASE' ? 'Compra' : 'Venta'

  function notifyConfirmed (operation) {
    if (operation.status === 'CONFIRMED' && !confirmedNotified.current) {
      confirmedNotified.current = true
      onConfirmed?.(operation)
    }
  }

  async function submit (event) {
    event.preventDefault()
    if (!quantityValid) {
      setState({ status: 'error', message: quantityError })
      return
    }
    if (!confirmed || !canOperate) return

    setState({ status: 'loading', message: '' })
    const body = {
      player_id: playerId,
      quantity: quantityNumber,
      quote_id: player.quote_id,
      quote_version: player.quote_version
    }

    try {
      const response = type === 'PURCHASE'
        ? await purchaseMarketTokens({ body, idempotencyKey, jwt })
        : await sellMarketTokens({ body, idempotencyKey, jwt })
      const operation = operationResponse(response)
      setResult(operation)
      setNeedsRecovery(!['CONFIRMED', 'REJECTED'].includes(operation.status))
      notifyConfirmed(operation)
      setState({ status: 'success', message: '' })
    } catch (error) {
      if (error.incompleteData) {
        setResult(error.incompleteData)
        setNeedsRecovery(true)
      }
      setState({ status: 'error', message: errorMessage(error) })
    }
  }

  async function recover () {
    setState({ status: 'loading', message: '' })
    try {
      const response = await getMarketOperation(idempotencyKey, jwt)
      const operation = operationLookupResponse(response)
      setResult((current) => ({ ...current, ...operation }))
      setNeedsRecovery(operation.status === 'PROCESSING' || operation.status === 'UNCERTAIN' || !['CONFIRMED', 'REJECTED'].includes(operation.status))
      notifyConfirmed(operation)
      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({ status: 'error', message: errorMessage(error) })
    }
  }

  return (
    <section ref={panelRef} className="market-operation-panel card" tabIndex={-1} aria-labelledby="operation-panel-title">
      <div className="market-operation-panel__heading">
        <div>
          <p className="eyebrow">Confirmar operación</p>
          <h2 id="operation-panel-title">{operationLabel} de {player.name}</h2>
        </div>
        <button className="button button--quiet" type="button" onClick={onClose} disabled={state.status === 'loading'}>
          Cerrar
        </button>
      </div>

      {!canOperate
        ? <p className="market-operation-panel__warning">La cotización ya no es operable. Actualizá el mercado antes de continuar.</p>
        : null}

      <dl className="market-operation-panel__facts">
        <div><dt>Precio unitario</dt><dd>{formatAmount(player.price_per_token)}</dd></div>
        <div><dt>Versión aceptada</dt><dd>{player.quote_version || 'Sin versión'}</dd></div>
        <div><dt>Total estimado</dt><dd>{formatAmount(estimatedTotal)}</dd></div>
      </dl>

      <form onSubmit={submit} className="market-operation-form">
        <label className="field" htmlFor="market-operation-quantity">
          <span>Cantidad</span>
          <input
            id="market-operation-quantity"
            type="number"
            min="1"
            max={quantityLimit}
            step="1"
            value={quantity}
            onChange={(event) => {
              setQuantity(event.target.value)
              setState((current) => current.status === 'error' ? { status: 'idle', message: '' } : current)
            }}
            aria-invalid={!quantityValid}
            aria-describedby={`market-operation-quantity-availability market-operation-quantity-message`}
            disabled={!canOperate || state.status === 'loading' || Boolean(result)}
          />
          {isSale
            ? <span id="market-operation-quantity-availability">Disponible para vender: {availableQuantity} tokens</span>
            : null}
          {!quantityValid ? <span id="market-operation-quantity-message" className="field-error" role="alert">{quantityError}</span> : null}
        </label>
        <label className="market-operation-confirmation">
          <input
            type="checkbox"
            checked={confirmed}
            onChange={(event) => setConfirmed(event.target.checked)}
            disabled={!canOperate || state.status === 'loading' || Boolean(result)}
          />
          <span>Confirmo esta {operationLabel.toLowerCase()} con la versión de cotización indicada.</span>
        </label>
        <button className="button button--primary" type="submit" disabled={!confirmed || !canOperate || !quantityValid || state.status === 'loading' || Boolean(result)}>
          {state.status === 'loading' ? 'Enviando…' : `Confirmar ${operationLabel.toLowerCase()}`}
        </button>
      </form>

      {state.status === 'error' ? <p className="status-message status-message--error" role="alert">{state.message}</p> : null}
      {result
        ? (
          <div className="market-operation-result">
            <OperationStatus status={result.status} />
            {result.total_amount !== undefined ? <p>Total confirmado: {formatAmount(result.total_amount)}</p> : null}
            {result.transaction_id ? <p>Transacción: {result.transaction_id}</p> : null}
            {needsRecovery
              ? <button className="button button--secondary" type="button" onClick={recover} disabled={state.status === 'loading'}>Consultar estado</button>
              : null}
          </div>
        )
        : null}
    </section>
  )
}
