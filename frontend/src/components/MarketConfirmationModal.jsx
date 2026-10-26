function formatAmount (value) {
  if (value === null || value === undefined || value === '') return 'Sin información'
  return Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function MarketConfirmationModal ({
  open,
  title,
  message,
  pricePerToken,
  quantity,
  total,
  confirmLabel,
  onConfirm,
  onCancel,
  variant = 'buy'
}) {
  if (!open) return null

  const variantClass = variant === 'sell' ? ' market-purchase-confirmation--sell' : ''

  return (
    <div
        className={`market-purchase-confirmation${variantClass}`}
        onClick={(event) => {
          if (event.target === event.currentTarget) onCancel()
        }}
    >
      <div
          className="market-purchase-confirmation__dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="market-confirmation-title"
      >
        <div className="market-purchase-confirmation__icon" aria-hidden="true">✓</div>
        <h3 id="market-confirmation-title">{title}</h3>
        <p>{message}</p>

        <div className="market-purchase-confirmation__summary">
          <div><span>Precio por token</span><strong>{formatAmount(pricePerToken)}</strong></div>
          <div><span>Cantidad</span><strong>{quantity}</strong></div>
          <div><span>Total</span><strong>{formatAmount(total)} créditos</strong></div>
        </div>

        <div className="market-purchase-confirmation__actions">
          <button className="button button--secondary" type="button" onClick={onCancel}>Cancelar</button>
          <button className="button button--buy" type="button" onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}
