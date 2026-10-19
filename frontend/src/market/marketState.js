const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const QUOTE_STATUS_ALIASES = {
  VALID: 'available',
  AVAILABLE: 'available',
  CURRENT: 'available',
  REPLACED: 'replaced',
  INVALIDATED: 'invalidated',
  EXPIRED: 'expired',
  HISTORICAL: 'historical',
  ABSENT: 'unavailable',
  UNAVAILABLE: 'unavailable'
}

const OPERATION_STATUS_ALIASES = {
  PROCESSING: 'pending',
  PENDING: 'pending',
  CONFIRMED: 'confirmed',
  REJECTED: 'rejected',
  UNCERTAIN: 'uncertain'
}

export function normalizeQuoteStatus (status) {
  if (!status) return 'unknown'
  return QUOTE_STATUS_ALIASES[String(status).toUpperCase()] || 'unknown'
}

export function quotePresentationStatus ({ quoteStatus, quoteAvailability, operable } = {}) {
  const availability = String(quoteAvailability || '').toUpperCase()
  const status = String(quoteStatus || '').toUpperCase()
  if (availability === 'ABSENT') return 'unavailable'
  if (availability === 'HISTORICAL') return 'historical'
  if (availability === 'CURRENT' && status === 'VALID' && operable === true) return 'available'
  const normalizedStatus = normalizeQuoteStatus(status)
  return normalizedStatus === 'available' ? 'unknown' : normalizedStatus
}

export function isMarketPlayerOperable (player) {
  return quotePresentationStatus(player) === 'available'
}

export function normalizeOperationStatus (status) {
  if (!status) return 'unknown'
  return OPERATION_STATUS_ALIASES[String(status).toUpperCase()] || 'unknown'
}

/** Converts only a documented market player identifier to a UUID. */
export function marketPlayerUuid (marketPlayerId) {
  if (typeof marketPlayerId !== 'string') return null
  const value = marketPlayerId.trim()
  const candidate = value.startsWith('ply_') ? value.slice(4) : value
  return UUID_PATTERN.test(candidate) ? candidate : null
}
