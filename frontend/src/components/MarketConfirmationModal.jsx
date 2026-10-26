import { CircleCheckBig } from 'lucide-react'

function formatAmount (value) {
  if (value === null || value === undefined || value === '') return 'Sin información'
  return Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function MarketConfirmationModal ({
  open, title, message, pricePerToken, quantity, total, confirmLabel,
  onConfirm, onCancel, variant = 'buy', children, confirmDisabled = false,
  totalLabel = 'Total', status = 'idle', successTitle = 'Operación realizada',
  successMessage = 'La operación se completó correctamente.'
}) {
  if (!open) return null

  const variantClass = variant === 'sell' ? ' market-purchase-confirmation--sell' : ''
  const isCritical = status === 'loading' || status === 'success'

  return (
    <div className={`market-purchase-confirmation${variantClass}`} onClick={(event) => {
      if (event.target === event.currentTarget && !isCritical) onCancel()
    }}>
      <div className="market-purchase-confirmation__dialog" role="dialog" aria-modal="true" aria-labelledby="market-confirmation-title">
        {status === 'success'
          ? (
            <div className="market-confirmation__success">
              <CircleCheckBig className="market-confirmation__success-icon" size={72} strokeWidth={1.8} aria-hidden="true" />
              <h3 id="market-confirmation-title" className="market-confirmation__success-title">{successTitle}</h3>
              <p className="market-confirmation__success-message">{successMessage}</p>
            </div>
          )
          : (
            <>
              <div className="market-purchase-confirmation__icon" aria-hidden="true">✓</div>
              <h3 id="market-confirmation-title">{title}</h3>
              <p>{message}</p>
              {children}
              <div className="market-purchase-confirmation__summary">
                <div><span>Precio por token</span><strong>{formatAmount(pricePerToken)}</strong></div>
                <div><span>Cantidad</span><strong>{quantity}</strong></div>
                <div><span>{totalLabel}</span><strong>{formatAmount(total)} créditos</strong></div>
              </div>
              <div className="market-purchase-confirmation__actions">
                <button className="button button--secondary" type="button" onClick={onCancel} disabled={status === 'loading'}>Cancelar</button>
                <button className={`button ${variant === 'sell' ? 'button--sell' : 'button--buy'}`} type="button" onClick={onConfirm} disabled={confirmDisabled || status === 'loading'}>{confirmLabel}</button>
              </div>
            </>
          )}
      </div>
    </div>
  )
}
