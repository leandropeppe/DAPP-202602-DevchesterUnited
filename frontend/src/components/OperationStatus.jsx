import StatusMessage from './StatusMessage.jsx'

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
  const knownStatus = Object.hasOwn(OPERATION_STATUS_MESSAGES, status)
  const resolvedStatus = knownStatus ? status : 'uncertain'
  const resolvedMessage = knownStatus
    ? (message || OPERATION_STATUS_MESSAGES[resolvedStatus])
    : (message || 'El resultado de la operación no se puede determinar.')

  return (
    <StatusMessage type={OPERATION_STATUS_TYPES[resolvedStatus]}>
      {resolvedMessage}
    </StatusMessage>
  )
}
