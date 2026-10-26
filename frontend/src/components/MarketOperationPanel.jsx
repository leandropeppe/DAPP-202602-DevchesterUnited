import { useRef, useState } from 'react'
import { getMarketOperation, purchaseMarketTokens, sellMarketTokens } from '../api/http.js'
import OperationStatus from './OperationStatus.jsx'
import MarketConfirmationModal from './MarketConfirmationModal.jsx'
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

export default function MarketOperationPanel ({ player, type, jwt, onClose, panelRef, maxQuantity = MAX_MARKET_QUANTITY, availableQuantity, availableBalance, currentHoldings, embedded = false, modalOnly = false, onConfirmed }) {
  const [quantity, setQuantity] = useState('1')
  const [confirmed, setConfirmed] = useState(false)
  const [showPurchaseConfirmation, setShowPurchaseConfirmation] = useState(modalOnly)
  const [state, setState] = useState({ status: 'idle', message: '' })
  const [result, setResult] = useState(null)
  const [needsRecovery, setNeedsRecovery] = useState(false)
  const [idempotencyKey] = useState(newIdempotencyKey)
  const confirmedNotified = useRef(false)

  const playerId = marketPlayerUuid(player.id)
  const canOperate = isMarketPlayerOperable(player) && playerId && player.quote_id && player.quote_version && player.price_per_token !== null && player.price_per_token !== undefined
  const isSale = type === 'SALE'
  const quantityNumber = Number(quantity)
  const quantityLimit = isSale
    ? Math.max(0, Number(maxQuantity) || 0)
    : Math.min(MAX_MARKET_QUANTITY, Math.max(0, Number(maxQuantity) || 0))
  const quantityError = quantityValidationMessage(quantity, quantityLimit)
  const quantityValid = quantityError === ''
  const estimatedTotal = Number(player.price_per_token) * quantityNumber
  const operationLabel = type === 'PURCHASE' ? 'Compra' : 'Venta'
  const balanceInsufficient = !isSale && availableBalance !== null && availableBalance !== undefined && Number.isFinite(Number(availableBalance)) && estimatedTotal > Number(availableBalance)
  const purchaseDisabled = balanceInsufficient || !quantityValid

  function notifyConfirmed (operation) {
    if (operation.status === 'CONFIRMED' && !confirmedNotified.current) {
      confirmedNotified.current = true
      onConfirmed?.(operation)
    }
  }

  async function executeOperation () {
    if (!modalOnly) setShowPurchaseConfirmation(false)
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
      if (modalOnly && operation.status === 'CONFIRMED') setShowPurchaseConfirmation(false)
    } catch (error) {
      if (error.incompleteData) {
        setResult(error.incompleteData)
        setNeedsRecovery(true)
      }

      setState({ status: 'error', message: errorMessage(error) })
    }
  }

  function submit (event) {
    event.preventDefault()

    if (!quantityValid) {
      setState({ status: 'error', message: quantityError })
      return
    }

    if (!canOperate || purchaseDisabled) return

    if (embedded) {
      setShowPurchaseConfirmation(true)
      return
    }

    if (!confirmed) return

    void executeOperation()
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

  const embeddedQuantityControl = (
      <div className="quantity-control">
        <button
            className="quantity-stepper"
            type="button"
            onClick={() => setQuantity(String(Math.max(1, quantityNumber - 1)))}
            disabled={!canOperate || state.status === 'loading' || Boolean(result) || quantityNumber <= 1}
            aria-label="Disminuir cantidad"
        >
          -
        </button>
        <input
            id={modalOnly ? 'market-sale-quantity' : 'player-detail-purchase-quantity'}
            type="number"
            min="1"
            max={quantityLimit}
            step="1"
            value={quantity}
            aria-label="Cantidad de tokens"
            onChange={(event) => {
              setQuantity(event.target.value)
              setState((current) => current.status === 'error' ? { status: 'idle', message: '' } : current)
            }}
            aria-invalid={!quantityValid}
            aria-describedby={modalOnly ? 'market-sale-quantity-message' : 'player-detail-purchase-quantity-message'}
            disabled={!canOperate || state.status === 'loading' || Boolean(result)}
        />
        <button
            className="quantity-stepper"
            type="button"
            onClick={() => setQuantity(String(Math.min(quantityLimit, quantityNumber + 1)))}
            disabled={!canOperate || state.status === 'loading' || Boolean(result) || quantityNumber >= quantityLimit}
            aria-label="Aumentar cantidad"
        >
          +
        </button>
      </div>
  )

  if (modalOnly) {
    return (
      <MarketConfirmationModal
          open={showPurchaseConfirmation}
          title="Confirmar venta"
          message={<>¿Querés vender tokens de <strong>{player.name}</strong>?</>}
          pricePerToken={player.price_per_token}
          quantity={quantityNumber}
          total={estimatedTotal}
          totalLabel="Total estimado"
          confirmLabel={state.status === 'loading' ? 'Vendiendo…' : 'Confirmar venta'}
          confirmDisabled={!canOperate || !quantityValid || state.status === 'loading' || Boolean(result)}
          variant="sell"
          onCancel={() => { setShowPurchaseConfirmation(false); onClose?.() }}
          onConfirm={() => { void executeOperation() }}
      >
        <div className="market-confirmation-quantity">
          <span>Tokens disponibles: <strong>{availableQuantity}</strong></span>
          {embeddedQuantityControl}
          {!quantityValid ? <small id="market-sale-quantity-message" className="field-error" role="alert">{quantityError}</small> : null}
          {state.status === 'error' ? <small className="field-error" role="alert">{state.message}</small> : null}
        </div>
      </MarketConfirmationModal>
    )
  }

  return (
      <section ref={panelRef} className={`market-operation-panel card${embedded ? ' market-operation-panel--embedded' : ''}`} tabIndex={-1} aria-labelledby="operation-panel-title">
        <div className="market-operation-panel__heading">
          <div>
            <p className="eyebrow">Confirmar operación</p>
            <h2 id="operation-panel-title">{embedded ? 'Comprar tokens' : `${operationLabel} de ${player.name}`}</h2>
          </div>

          {embedded
              ? (
                  <div className="market-operation-panel__balance">
                    <span className="market-operation-panel__balance-icon" aria-hidden="true">▣</span>
                    <span>Saldo disponible</span>
                    <strong>{formatAmount(availableBalance)} créditos</strong>
                  </div>
              )
              : null}

          {!embedded && (
              <button className="button button--quiet" type="button" onClick={onClose} disabled={state.status === 'loading'}>
                Cerrar
              </button>
          )}
        </div>

        {!canOperate
            ? <p className="market-operation-panel__warning">La cotización ya no es operable. Actualizá el mercado antes de continuar.</p>
            : null}

        {embedded
            ? (
                <form onSubmit={submit} className="market-operation-form market-operation-form--embedded">
                  <div className="market-operation-panel__embedded-facts">
                    <div className="market-operation-panel__metric">
                      <span>Precio actual por token</span>
                      <strong>{formatAmount(player.price_per_token)}</strong>
                    </div>

                    <div className="market-operation-panel__metric">
                      <span>Mis tokens de este jugador</span>
                      <strong>{Number(currentHoldings || 0).toLocaleString('es-AR')}</strong>
                    </div>

                    <label className="market-operation-panel__metric market-operation-panel__quantity" htmlFor="player-detail-purchase-quantity">
                      <span>Cantidad</span>
                      {embeddedQuantityControl}
                      {balanceInsufficient ? <small className="field-error" role="alert">Saldo insuficiente para esta compra.</small> : null}
                      {!quantityValid ? <small id="player-detail-purchase-quantity-message" className="field-error" role="alert">{quantityError}</small> : null}
                    </label>

                    <div className="market-operation-panel__metric">
                      <span>Costo total</span>
                      <strong>{formatAmount(estimatedTotal)}</strong>
                    </div>
                  </div>

                  <button
                      className="button button--buy player-detail-purchase-submit"
                      type="submit"
                      disabled={!canOperate || purchaseDisabled || state.status === 'loading' || Boolean(result)}
                  >
                    {state.status === 'loading' ? 'Comprando…' : 'Comprar tokens'}
                  </button>
                </form>
            )
            : (
                <>
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
                          aria-describedby="market-operation-quantity-availability market-operation-quantity-message"
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

                    <button
                        className="button button--primary"
                        type="submit"
                        disabled={!confirmed || !canOperate || purchaseDisabled || state.status === 'loading' || Boolean(result)}
                    >
                      {state.status === 'loading' ? 'Enviando…' : `Confirmar ${operationLabel.toLowerCase()}`}
                    </button>
                  </form>
                </>
            )}

        <MarketConfirmationModal
            open={showPurchaseConfirmation}
            title="Confirmar compra"
            message={(
              <>
                ¿Estás seguro de que querés comprar{' '}
                <strong>{quantityNumber} {quantityNumber === 1 ? 'token' : 'tokens'}</strong>
                {' '}de <strong>{player.name}</strong>?
              </>
            )}
            pricePerToken={player.price_per_token}
            quantity={quantityNumber}
            total={estimatedTotal}
            confirmLabel="Confirmar compra"
            variant="buy"
            onCancel={() => setShowPurchaseConfirmation(false)}
            onConfirm={() => { void executeOperation() }}
        />

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
