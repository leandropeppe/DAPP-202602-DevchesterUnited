import { createPortal } from 'react-dom'

function formatAmount (value) {
  if (value === null || value === undefined || value === '') return 'No disponible'
  return Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 8 })
}

export default function MarketP2PConfirmationModal ({ open, mode, subject, quote, quantity, maxQuantity, state, message, onQuantityChange, onConfirm, onCancel }) {
  if (!open) return null
  const isPublish = mode === 'publish'
  const isCancel = mode === 'cancel'
  const unitPrice = quote?.unitPrice
  const total = isCancel ? null : Number(unitPrice || 0) * Number(quantity || 0)
  const quoteAvailable = isCancel || Boolean(quote?.quoteId && quote?.quoteVersion && quote?.unitPrice !== null && quote?.unitPrice !== undefined)
  const validQuantity = isCancel || quoteAvailable && Number.isInteger(Number(quantity)) && Number(quantity) > 0 && Number(quantity) <= Number(maxQuantity || Number.MAX_SAFE_INTEGER)
  const title = isPublish ? 'Confirmar publicación' : isCancel ? 'Confirmar cancelación' : 'Confirmar compra'
  const actionLabel = state === 'loading' ? 'Enviando...' : isPublish ? 'Publicar oferta' : isCancel ? 'Cancelar oferta' : 'Comprar tokens'

  return createPortal(
    <div className="market-purchase-confirmation">
      <div className="market-purchase-confirmation__dialog" role="dialog" aria-modal="true" aria-labelledby="p2p-confirmation-title">
        {state === 'success'
          ? <><h3 id="p2p-confirmation-title">Operacion confirmada</h3><p>La operacion se completo correctamente.</p><button className="button button--primary" type="button" onClick={onCancel}>Cerrar</button></>
          : <>
            <h3 id="p2p-confirmation-title">{title}</h3>
            <p>{isPublish ? 'La cotizacion se toma exclusivamente del mercado oficial.' : isCancel ? 'Se liberara unicamente el remanente de la oferta.' : 'La compra utilizara la cotizacion oficial observada.'}</p>
            <p><strong>{subject}</strong></p>
            {!isCancel ? <dl className="market-p2p-confirmation__facts"><div><dt>Precio unitario</dt><dd>{formatAmount(unitPrice)}</dd></div><div><dt>Total estimado</dt><dd>{formatAmount(total)} creditos</dd></div></dl> : null}
            {!isCancel ? <label className="field" htmlFor="p2p-confirmation-quantity"><span>Cantidad</span><input id="p2p-confirmation-quantity" type="number" min="1" max={maxQuantity || undefined} step="1" value={quantity} onChange={(event) => onQuantityChange?.(event.target.value)} disabled={state === 'loading'} /></label> : null}
            {message ? <p className="field-error" role="alert">{message}</p> : null}
            <div className="market-purchase-confirmation__actions"><button className="button button--secondary" type="button" onClick={onCancel} disabled={state === 'loading'}>Volver</button><button className="button button--buy" type="button" onClick={onConfirm} disabled={state === 'loading' || !validQuantity}>{actionLabel}</button></div>
          </>}
      </div>
    </div>,
    document.body
  )
}
