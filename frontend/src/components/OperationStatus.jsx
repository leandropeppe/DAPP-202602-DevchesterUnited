import StatusMessage from './StatusMessage.jsx'
import { normalizeOperationStatus } from '../market/marketState.js'

const OPERATION_STATUS_MESSAGES = {
  pending: 'La operación está pendiente de confirmación.',
  confirmed: 'La operación fue confirmada.',
  rejected: 'La operación fue rechazada.',
  uncertain: 'El resultado de la operación es incierto. Consulta su estado antes de reintentar.'
}

const OPERATION_STATUS_TYPES = {
  pending: 'info',
  confirmed: 'success',
  rejected: 'error',
  uncertain: 'error'
}

export default function OperationStatus ({ status, message }) {
  const resolvedStatus = normalizeOperationStatus(status)
  const knownStatus = Object.hasOwn(OPERATION_STATUS_MESSAGES, resolvedStatus)
  const resolvedMessage = knownStatus
    ? (message || OPERATION_STATUS_MESSAGES[resolvedStatus])
    : (message || 'El resultado de la operación no se puede determinar.')

  return (
    <StatusMessage type={knownStatus ? OPERATION_STATUS_TYPES[resolvedStatus] : 'error'}>
      {resolvedMessage}
    </StatusMessage>
  )
}
