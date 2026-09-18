const JWT_STORAGE_KEY = 'devchester.jwt'
const API_KEY_STORAGE_KEY = 'devchester.api-key'

let memorySession = {
  jwt: null,
  apiKey: null
}

function getSessionStorage () {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const storage = window.sessionStorage
    const probeKey = '__devchester_session_probe__'
    storage.setItem(probeKey, '1')
    storage.removeItem(probeKey)
    return storage
  } catch {
    return null
  }
}

function decodeJwtPayload (token) {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null

    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    return JSON.parse(window.atob(padded))
  } catch {
    return null
  }
}

export function getJwtExpiration (token) {
  if (!token) return null
  const expiration = decodeJwtPayload(token)?.exp
  return Number.isFinite(expiration) ? expiration : null
}

export function isJwtExpired (token, now = Date.now()) {
  const expiration = getJwtExpiration(token)
  return expiration !== null && expiration * 1000 <= now
}

export function readSession () {
  const storage = getSessionStorage()
  const session = storage
    ? {
        jwt: storage.getItem(JWT_STORAGE_KEY),
        apiKey: storage.getItem(API_KEY_STORAGE_KEY)
      }
    : { ...memorySession }

  if (session.jwt && isJwtExpired(session.jwt)) {
    clearJwt()
    session.jwt = null
  }

  return session
}

export function saveJwt (jwt) {
  const storage = getSessionStorage()
  if (storage) {
    storage.setItem(JWT_STORAGE_KEY, jwt)
  } else {
    memorySession = { ...memorySession, jwt }
  }
}

export function saveApiKey (apiKey) {
  const storage = getSessionStorage()
  if (storage) {
    storage.setItem(API_KEY_STORAGE_KEY, apiKey)
  } else {
    memorySession = { ...memorySession, apiKey }
  }
}

export function clearJwt () {
  const storage = getSessionStorage()
  if (storage) {
    storage.removeItem(JWT_STORAGE_KEY)
  }
  memorySession = { ...memorySession, jwt: null }
}

export function clearApiKey () {
  const storage = getSessionStorage()
  if (storage) {
    storage.removeItem(API_KEY_STORAGE_KEY)
  }
  memorySession = { ...memorySession, apiKey: null }
}

export function clearSession () {
  clearJwt()
  clearApiKey()
}

export function isPersistentStorageAvailable () {
  return getSessionStorage() !== null
}
