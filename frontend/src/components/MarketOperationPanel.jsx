import { useEffect, useRef, useState } from 'react'
import { getMarketOperation, purchaseMarketTokens, sellMarketTokens } from '../api/http.js'
import OperationStatus from './OperationStatus.jsx'
import MarketConfirmationModal from './MarketConfirmationModal.jsx'
import { isMarketPlayerOperable, marketPlayerUuid } from '../market/marketState.js'
import { errorMessage } from '../views/formUtils.js'
import { Wallet } from 'lucide-react'

function formatAmount (value) {
  if (value === null || value === undefined || value === '') {
    return 'Sin información'
  }

  return Number(value).toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function newIdempotencyKey () {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }

  const randomBytes = new Uint8Array(16)

  globalThis.crypto.getRandomValues(randomBytes)

  const randomPart = Array
      .from(
          randomBytes,
          byte => byte
              .toString(16)
              .padStart(2, '0')
      )
      .join('')

  return `market-${randomPart}`
}

const MAX_MARKET_QUANTITY = 100

function quantityValidationMessage (
    value,
    maxQuantity = MAX_MARKET_QUANTITY
) {
  if (value === '') {
    return 'Ingresá una cantidad de tokens.'
  }

  const parsed = Number(value)

  if (!Number.isInteger(parsed)) {
    return 'La cantidad debe ser un número entero.'
  }

  if (parsed < 1) {
    return 'La cantidad debe ser al menos 1 token.'
  }

  if (parsed > maxQuantity) {
    return `La cantidad no puede superar ${maxQuantity} tokens.`
  }

  return ''
}

function isObject (value) {
  return (
      value !== null &&
      typeof value === 'object' &&
      !Array.isArray(value)
  )
}

function operationResponse (response) {
  const data = response?.data

  if (
      !isObject(data) ||
      typeof data.status !== 'string' ||
      !data.status.trim() ||
      typeof data.updated_at !== 'string' ||
      !data.updated_at
  ) {
    const error = new Error(
        'El servidor devolvió una respuesta incompleta para la operación.'
    )

    error.incompleteData = {}

    throw error
  }

  return data
}

function operationLookupResponse (response) {
  const data = response?.data

  if (
      !isObject(data) ||
      typeof data.idempotency_key !== 'string' ||
      !data.idempotency_key.trim() ||
      typeof data.operation_type !== 'string' ||
      !data.operation_type.trim() ||
      typeof data.status !== 'string' ||
      !data.status.trim() ||
      typeof data.updated_at !== 'string' ||
      !data.updated_at
  ) {
    throw new Error(
        'El servidor devolvió un estado incompleto para la operación.'
    )
  }

  return data
}

function handleIncompleteOperation (
    error,
    setResult,
    setNeedsRecovery
) {
  if (!error.incompleteData) {
    return
  }

  setResult(error.incompleteData)
  setNeedsRecovery(true)
}

function QuantityControl ({
                            quantity,
                            quantityNumber,
                            quantityLimit,
                            canOperate,
                            disabled,
                            inputId,
                            describedBy,
                            invalid,
                            onChange
                          }) {
  return (
      <div className="quantity-control">
        <button
            className="quantity-stepper"
            type="button"
            onClick={() =>
                onChange(
                    String(
                        Math.max(
                            1,
                            quantityNumber - 1
                        )
                    )
                )
            }
            disabled={
                !canOperate ||
                disabled ||
                quantityNumber <= 1
            }
            aria-label="Disminuir cantidad"
        >
          -
        </button>

        <input
            id={inputId}
            type="number"
            min="1"
            max={quantityLimit}
            step="1"
            value={quantity}
            aria-label="Cantidad de tokens"
            onChange={event =>
                onChange(event.target.value)
            }
            aria-invalid={invalid}
            aria-describedby={describedBy}
            disabled={!canOperate || disabled}
        />

        <button
            className="quantity-stepper"
            type="button"
            onClick={() =>
                onChange(
                    String(
                        Math.min(
                            quantityLimit,
                            quantityNumber + 1
                        )
                    )
                )
            }
            disabled={
                !canOperate ||
                disabled ||
                quantityNumber >= quantityLimit
            }
            aria-label="Aumentar cantidad"
        >
          +
        </button>
      </div>
  )
}

function OperationResult ({
                            result,
                            needsRecovery,
                            state,
                            recover
                          }) {
  if (!result) {
    return null
  }

  return (
      <div className="market-operation-result">
        <OperationStatus
            status={result.status}
        />

        {result.total_amount !== undefined ? (
            <p>
              Total confirmado:{' '}
              {formatAmount(result.total_amount)}
            </p>
        ) : null}

        {result.transaction_id ? (
            <p>
              Transacción:{' '}
              {result.transaction_id}
            </p>
        ) : null}

        {needsRecovery ? (
            <button
                className="button button--secondary"
                type="button"
                onClick={recover}
                disabled={state.status === 'loading'}
            >
              Consultar estado
            </button>
        ) : null}
      </div>
  )
}

function EmbeddedOperationForm ({
                                  player,
                                  currentHoldings,
                                  availableBalance,
                                  quantity,
                                  quantityNumber,
                                  quantityLimit,
                                  quantityError,
                                  quantityValid,
                                  balanceInsufficient,
                                  canOperate,
                                  state,
                                  result,
                                  onQuantityChange,
                                  onSubmit
                                }) {
  return (
      <form
          onSubmit={onSubmit}
          className="market-operation-form market-operation-form--embedded"
      >
        <div className="market-operation-panel__embedded-facts">
          <div className="market-operation-panel__metric">
          <span>
            Precio actual por token
          </span>

            <strong>
              {formatAmount(
                  player.price_per_token
              )}
            </strong>
          </div>

          <div className="market-operation-panel__metric">
          <span>
            Mis tokens de este jugador
          </span>

            <strong>
              {Number(
                  currentHoldings || 0
              ).toLocaleString('es-AR')}
            </strong>
          </div>

          <label
              className="market-operation-panel__metric market-operation-panel__quantity"
              htmlFor="player-detail-purchase-quantity"
          >
          <span>
            Cantidad
          </span>

            <QuantityControl
                quantity={quantity}
                quantityNumber={quantityNumber}
                quantityLimit={quantityLimit}
                canOperate={canOperate}
                disabled={
                    state.status === 'loading' ||
                    Boolean(result)
                }
                inputId="player-detail-purchase-quantity"
                describedBy="player-detail-purchase-quantity-message"
                invalid={!quantityValid}
                onChange={onQuantityChange}
            />

            {balanceInsufficient ? (
                <small
                    className="field-error"
                    role="alert"
                >
                  Saldo insuficiente para esta compra.
                </small>
            ) : null}

            {!quantityValid ? (
                <small
                    id="player-detail-purchase-quantity-message"
                    className="field-error"
                    role="alert"
                >
                  {quantityError}
                </small>
            ) : null}
          </label>

          <div className="market-operation-panel__metric">
          <span>
            Costo total
          </span>

            <strong>
              {formatAmount(
                  Number(
                      player.price_per_token
                  ) * quantityNumber
              )}
            </strong>
          </div>
        </div>

        <button
            className="button button--buy player-detail-purchase-submit"
            type="submit"
            disabled={
                !canOperate ||
                balanceInsufficient ||
                !quantityValid ||
                state.status === 'loading' ||
                Boolean(result)
            }
        >
          {state.status === 'loading'
              ? 'Comprando…'
              : 'Comprar tokens'}
        </button>
      </form>
  )
}

function StandardOperationForm ({
                                  player,
                                  type,
                                  availableQuantity,
                                  quantity,
                                  quantityNumber,
                                  quantityLimit,
                                  quantityError,
                                  quantityValid,
                                  isSale,
                                  operationLabel,
                                  canOperate,
                                  state,
                                  result,
                                  confirmed,
                                  onQuantityChange,
                                  onConfirmed,
                                  onSubmit
                                }) {
  return (
      <>
        <dl className="market-operation-panel__facts">
          <div>
            <dt>
              Precio unitario
            </dt>

            <dd>
              {formatAmount(
                  player.price_per_token
              )}
            </dd>
          </div>

          <div>
            <dt>
              Versión aceptada
            </dt>

            <dd>
              {player.quote_version ||
                  'Sin versión'}
            </dd>
          </div>

          <div>
            <dt>
              Total estimado
            </dt>

            <dd>
              {formatAmount(
                  Number(
                      player.price_per_token
                  ) * quantityNumber
              )}
            </dd>
          </div>
        </dl>

        <form
            onSubmit={onSubmit}
            className="market-operation-form"
        >
          <label
              className="field"
              htmlFor="market-operation-quantity"
          >
          <span>
            Cantidad
          </span>

            <input
                id="market-operation-quantity"
                type="number"
                min="1"
                max={quantityLimit}
                step="1"
                value={quantity}
                onChange={event =>
                    onQuantityChange(
                        event.target.value
                    )
                }
                aria-invalid={!quantityValid}
                aria-describedby="market-operation-quantity-availability market-operation-quantity-message"
                disabled={
                    !canOperate ||
                    state.status === 'loading' ||
                    Boolean(result)
                }
            />

            {isSale ? (
                <span id="market-operation-quantity-availability">
              Disponible para vender:{' '}
                  {availableQuantity} tokens
            </span>
            ) : null}

            {!quantityValid ? (
                <span
                    id="market-operation-quantity-message"
                    className="field-error"
                    role="alert"
                >
              {quantityError}
            </span>
            ) : null}
          </label>

          <label className="market-operation-confirmation">
            <input
                type="checkbox"
                checked={confirmed}
                onChange={event =>
                    onConfirmed(
                        event.target.checked
                    )
                }
                disabled={
                    !canOperate ||
                    state.status === 'loading' ||
                    Boolean(result)
                }
            />

            <span>
            Confirmo esta{' '}
              {operationLabel.toLowerCase()}{' '}
              con la versión de cotización indicada.
          </span>
          </label>

          <button
              className="button button--primary"
              type="submit"
              disabled={
                  !confirmed ||
                  !canOperate ||
                  !quantityValid ||
                  state.status === 'loading' ||
                  Boolean(result)
              }
          >
            {state.status === 'loading'
                ? 'Enviando…'
                : `Confirmar ${operationLabel.toLowerCase()}`}
          </button>
        </form>
      </>
  )
}

function SaleConfirmationView ({
                                 player,
                                 state,
                                 result,
                                 show,
                                 quantity,
                                 quantityNumber,
                                 total,
                                 quantityError,
                                 quantityValid,
                                 canOperate,
                                 availableQuantity,
                                 quantityLimit,
                                 onQuantityChange,
                                 onCancel,
                                 onConfirm
                               }) {
  const confirmationStatus =
      state.status === 'loading'
          ? 'loading'
          : (
              state.status === 'success' &&
              result?.status === 'CONFIRMED'
          )
              ? 'success'
              : 'idle'

  const confirmLabel =
      state.status === 'loading'
          ? 'Vendiendo…'
          : 'Confirmar venta'

  return (
      <MarketConfirmationModal
          open={show}
          title="Confirmar venta"
          message={
            <>
              ¿Querés vender tokens de{' '}
              <strong>
                {player.name}
              </strong>
              ?
            </>
          }
          pricePerToken={player.price_per_token}
          quantity={quantityNumber}
          total={total}
          totalLabel="Total estimado"
          confirmLabel={confirmLabel}
          status={confirmationStatus}
          successTitle="Venta realizada"
          successMessage="La operación se completó correctamente."
          confirmDisabled={
              !canOperate ||
              !quantityValid ||
              state.status === 'loading' ||
              Boolean(result)
          }
          variant="sell"
          onCancel={onCancel}
          onConfirm={onConfirm}
      >
        <div className="market-confirmation-quantity">
        <span>
          Tokens disponibles:{' '}
          <strong>
            {availableQuantity}
          </strong>
        </span>

          <QuantityControl
              quantity={quantity}
              quantityNumber={quantityNumber}
              quantityLimit={quantityLimit}
              canOperate={canOperate}
              disabled={
                  state.status === 'loading' ||
                  Boolean(result)
              }
              inputId="market-sale-quantity"
              describedBy="market-sale-quantity-message"
              invalid={!quantityValid}
              onChange={onQuantityChange}
          />

          {!quantityValid ? (
              <small
                  id="market-sale-quantity-message"
                  className="field-error"
                  role="alert"
              >
                {quantityError}
              </small>
          ) : null}

          {state.status === 'error' ? (
              <small
                  className="field-error"
                  role="alert"
              >
                {state.message}
              </small>
          ) : null}
        </div>
      </MarketConfirmationModal>
  )
}

export default function MarketOperationPanel ({
                                                player,
                                                type,
                                                jwt,
                                                onClose,
                                                panelRef,
                                                maxQuantity = MAX_MARKET_QUANTITY,
                                                availableQuantity,
                                                availableBalance,
                                                currentHoldings,
                                                embedded = false,
                                                modalOnly = false,
                                                onConfirmed
                                              }) {
  const [quantity, setQuantity] =
      useState('1')

  const [confirmed, setConfirmed] =
      useState(false)

  const [
    showPurchaseConfirmation,
    setShowPurchaseConfirmation
  ] = useState(modalOnly)

  const [state, setState] = useState({
    status: 'idle',
    message: ''
  })

  const [result, setResult] =
      useState(null)

  const [
    needsRecovery,
    setNeedsRecovery
  ] = useState(false)

  const [idempotencyKey] =
      useState(newIdempotencyKey)

  const confirmedNotified =
      useRef(false)

  const operationInFlight =
      useRef(false)

  const successTimer =
      useRef(null)

  useEffect(
      () => () => {
        if (successTimer.current) {
          clearTimeout(
              successTimer.current
          )
        }
      },
      []
  )

  const playerId =
      marketPlayerUuid(player.id)

  const canOperate =
      isMarketPlayerOperable(player) &&
      playerId &&
      player.quote_id &&
      player.quote_version &&
      player.price_per_token !== null &&
      player.price_per_token !== undefined

  const isSale =
      type === 'SALE'

  const confirmationStatus =
      state.status === 'loading'
          ? 'loading'
          : (
              state.status === 'success' &&
              result?.status === 'CONFIRMED'
          )
              ? 'success'
              : 'idle'

  const quantityNumber =
      Number(quantity)

  const quantityLimit =
      isSale
          ? Math.max(
              0,
              Number(maxQuantity) || 0
          )
          : Math.min(
              MAX_MARKET_QUANTITY,
              Math.max(
                  0,
                  Number(maxQuantity) || 0
              )
          )

  const quantityError =
      quantityValidationMessage(
          quantity,
          quantityLimit
      )

  const quantityValid =
      quantityError === ''

  const estimatedTotal =
      Number(
          player.price_per_token
      ) * quantityNumber

  const operationLabel =
      type === 'PURCHASE'
          ? 'Compra'
          : 'Venta'

  const balanceInsufficient =
      !isSale &&
      availableBalance !== null &&
      availableBalance !== undefined &&
      Number.isFinite(
          Number(availableBalance)
      ) &&
      estimatedTotal >
      Number(availableBalance)

  function notifyConfirmed (operation) {
    if (
        operation.status === 'CONFIRMED' &&
        !confirmedNotified.current
    ) {
      confirmedNotified.current = true

      onConfirmed?.(operation)
    }
  }

  async function executeOperation () {
    if (operationInFlight.current) {
      return
    }

    operationInFlight.current = true

    setState({
      status: 'loading',
      message: ''
    })

    const body = {
      player_id: playerId,
      quantity: quantityNumber,
      quote_id: player.quote_id,
      quote_version: player.quote_version
    }

    try {
      const response =
          type === 'PURCHASE'
              ? await purchaseMarketTokens({
                body,
                idempotencyKey,
                jwt
              })
              : await sellMarketTokens({
                body,
                idempotencyKey,
                jwt
              })

      const operation =
          operationResponse(response)

      setResult(operation)

      setNeedsRecovery(
          ![
            'CONFIRMED',
            'REJECTED'
          ].includes(
              operation.status
          )
      )

      setState({
        status: 'success',
        message: ''
      })

      if (
          operation.status === 'CONFIRMED'
      ) {
        setShowPurchaseConfirmation(true)

        successTimer.current =
            setTimeout(() => {
              notifyConfirmed(operation)

              setShowPurchaseConfirmation(
                  false
              )

              if (modalOnly) {
                onClose?.()
              }

              successTimer.current =
                  null
            }, 2000)
      }
    } catch (error) {
      handleIncompleteOperation(
          error,
          setResult,
          setNeedsRecovery
      )

      setState({
        status: 'error',
        message: errorMessage(error)
      })
    } finally {
      operationInFlight.current =
          false
    }
  }

  function submit (event) {
    event.preventDefault()

    if (!quantityValid) {
      return setState({
        status: 'error',
        message: quantityError
      })
    }

    if (!canOperate) {
      return
    }

    if (embedded) {
      return setShowPurchaseConfirmation(
          true
      )
    }

    if (confirmed) {
      void executeOperation()
    }
  }

  async function recover () {
    setState({
      status: 'loading',
      message: ''
    })

    try {
      const operation =
          operationLookupResponse(
              await getMarketOperation(
                  idempotencyKey,
                  jwt
              )
          )

      setResult(current => ({
        ...current,
        ...operation
      }))

      setNeedsRecovery(
          operation.status === 'PROCESSING' ||
          operation.status === 'UNCERTAIN' ||
          ![
            'CONFIRMED',
            'REJECTED'
          ].includes(
              operation.status
          )
      )

      notifyConfirmed(operation)

      setState({
        status: 'success',
        message: ''
      })
    } catch (error) {
      setState({
        status: 'error',
        message: errorMessage(error)
      })
    }
  }

  function handleQuantityChange (value) {
    setQuantity(value)

    setState(current =>
        current.status === 'error'
            ? {
              status: 'idle',
              message: ''
            }
            : current
    )
  }

  if (modalOnly) {
    return (
        <SaleConfirmationView
            player={player}
            state={state}
            result={result}
            show={showPurchaseConfirmation}
            quantity={quantity}
            quantityNumber={quantityNumber}
            total={estimatedTotal}
            quantityError={quantityError}
            quantityValid={quantityValid}
            canOperate={canOperate}
            availableQuantity={availableQuantity}
            quantityLimit={quantityLimit}
            onQuantityChange={handleQuantityChange}
            onCancel={() => {
              setShowPurchaseConfirmation(
                  false
              )

              onClose?.()
            }}
            onConfirm={() => {
              void executeOperation()
            }}
        />
    )
  }

  return (
      <section
          ref={panelRef}
          className={`market-operation-panel card${
              embedded
                  ? ' market-operation-panel--embedded'
                  : ''
          }`}
          tabIndex={-1}
          aria-labelledby="operation-panel-title"
      >
        <div className="market-operation-panel__heading">
          <div>
            <p className="eyebrow">
              Confirmar operación
            </p>

            <h2 id="operation-panel-title">
              {embedded
                  ? 'Comprar tokens'
                  : `${operationLabel} de ${player.name}`}
            </h2>
          </div>

          {embedded ? (
              <div className="market-operation-panel__balance">
            <span
                className="market-operation-panel__balance-icon"
                aria-hidden="true"
            >
              <Wallet
                  size={18}
                  strokeWidth={2.2}
              />
            </span>

                <span>
              Saldo disponible
            </span>

                <strong>
                  {formatAmount(
                      availableBalance
                  )}{' '}
                  créditos
                </strong>
              </div>
          ) : null}

          {!embedded ? (
              <button
                  className="button button--quiet"
                  type="button"
                  onClick={onClose}
                  disabled={
                      state.status === 'loading'
                  }
              >
                Cerrar
              </button>
          ) : null}
        </div>

        {!canOperate ? (
            <p className="market-operation-panel__warning">
              La cotización ya no es operable.
              Actualizá el mercado antes de continuar.
            </p>
        ) : null}

        {embedded ? (
            <EmbeddedOperationForm
                player={player}
                currentHoldings={currentHoldings}
                availableBalance={availableBalance}
                quantity={quantity}
                quantityNumber={quantityNumber}
                quantityLimit={quantityLimit}
                quantityError={quantityError}
                quantityValid={quantityValid}
                balanceInsufficient={balanceInsufficient}
                canOperate={canOperate}
                state={state}
                result={result}
                onQuantityChange={handleQuantityChange}
                onSubmit={submit}
            />
        ) : (
            <StandardOperationForm
                player={player}
                type={type}
                availableQuantity={availableQuantity}
                quantity={quantity}
                quantityNumber={quantityNumber}
                quantityLimit={quantityLimit}
                quantityError={quantityError}
                quantityValid={quantityValid}
                isSale={isSale}
                operationLabel={operationLabel}
                canOperate={canOperate}
                state={state}
                result={result}
                confirmed={confirmed}
                onQuantityChange={handleQuantityChange}
                onConfirmed={setConfirmed}
                onSubmit={submit}
            />
        )}

        <MarketConfirmationModal
            open={showPurchaseConfirmation}
            title="Confirmar compra"
            message={
              <>
                ¿Estás seguro de que querés comprar{' '}
                <strong>
                  {quantityNumber}{' '}
                  {quantityNumber === 1
                      ? 'token'
                      : 'tokens'}
                </strong>{' '}
                de{' '}
                <strong>
                  {player.name}
                </strong>
                ?
              </>
            }
            pricePerToken={player.price_per_token}
            quantity={quantityNumber}
            total={estimatedTotal}
            confirmLabel="Confirmar compra"
            status={confirmationStatus}
            successTitle="Compra realizada"
            successMessage="La operación se completó correctamente."
            variant="buy"
            onCancel={() =>
                setShowPurchaseConfirmation(
                    false
                )
            }
            onConfirm={() => {
              void executeOperation()
            }}
        />

        {state.status === 'error' ? (
            <p
                className="status-message status-message--error"
                role="alert"
            >
              {state.message}
            </p>
        ) : null}

        {!embedded ? (
            <OperationResult
                result={result}
                needsRecovery={needsRecovery}
                state={state}
                recover={recover}
            />
        ) : null}
      </section>
  )
}