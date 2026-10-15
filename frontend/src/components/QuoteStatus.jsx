import StatusMessage from './StatusMessage.jsx'

const QUOTE_STATUS_MESSAGES = {
  available: 'Cotización disponible.',
  unavailable: 'Cotización no disponible.',
  historical: 'Cotización histórica.',
  replaced: 'Cotización reemplazada.',
  invalidated: 'Cotización invalidada.',
  expired: 'Cotización expirada.'
}

const NON_OPERABLE_WARNING = 'no está disponible para operar.'
const UNKNOWN_STATUS_WARNING = 'El estado de la cotización no se puede determinar.'

function ensureMessages (message, requiredMessages) {
  return requiredMessages.reduce((resolvedMessage, requiredMessage) => {
    if (resolvedMessage.includes(requiredMessage)) return resolvedMessage
    return resolvedMessage ? `${resolvedMessage} ${requiredMessage}` : requiredMessage
  }, message || '')
}

export default function QuoteStatus ({ status, price, message }) {
  const knownStatus = Object.hasOwn(QUOTE_STATUS_MESSAGES, status)
  const isOperable = status === 'available'
  const type = isOperable ? 'success' : 'info'
  const baseMessage = message || (knownStatus ? QUOTE_STATUS_MESSAGES[status] : '')
  let requiredMessages = []
  if (!isOperable) {
    requiredMessages = knownStatus
      ? [NON_OPERABLE_WARNING]
      : [UNKNOWN_STATUS_WARNING, NON_OPERABLE_WARNING]
  }
  const resolvedMessage = ensureMessages(baseMessage, requiredMessages)
  const priceText = isOperable && price ? ` Precio: ${price}.` : ''

  return (
    <StatusMessage type={type}>
      {resolvedMessage}{priceText}
    </StatusMessage>
  )
}
