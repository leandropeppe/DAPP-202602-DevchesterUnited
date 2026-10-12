import StatusMessage from './StatusMessage.jsx'

const MARKET_STATE_MESSAGES = {
  loading: 'Cargando información del mercado…',
  error: 'No fue posible cargar la información del mercado.',
  unavailable: 'El mercado no está disponible temporalmente.',
  info: 'Información del mercado no disponible.'
}

const MARKET_STATE_TYPES = {
  loading: 'info',
  error: 'error',
  unavailable: 'error',
  info: 'info'
}

export default function MarketStateMessage ({ status, message }) {
  const knownStatus = Object.prototype.hasOwnProperty.call(MARKET_STATE_MESSAGES, status)
  const resolvedStatus = knownStatus ? status : 'info'
  const resolvedMessage = knownStatus
    ? (message || MARKET_STATE_MESSAGES[resolvedStatus])
    : (message || 'El estado del mercado no se puede determinar.')

  return (
    <StatusMessage type={MARKET_STATE_TYPES[resolvedStatus]}>
      {resolvedMessage}
    </StatusMessage>
  )
}
