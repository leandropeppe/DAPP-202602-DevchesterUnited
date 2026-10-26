import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import {
  clearApiKey,
  clearJwt,
  clearSession,
  getJwtExpiration,
  isJwtExpired,
  isPersistentStorageAvailable,
  readSession,
  saveApiKey,
  saveJwt
} from './storage.js'

const SessionContext = createContext(null)

function currentSession () {
  const session = readSession()
  return {
    ...session,
    jwtExpiresAt: getJwtExpiration(session.jwt)
  }
}

export function SessionProvider ({ children }) {
  const [session, setSession] = useState(currentSession)
  const storageAvailable = isPersistentStorageAvailable()

  useEffect(() => {
    if (!session.jwt || session.jwtExpiresAt === null) return undefined

    const maxTimerDuration = 2_147_483_647
    const remaining = Math.min(maxTimerDuration, Math.max(0, session.jwtExpiresAt * 1000 - Date.now()))
    const timer = window.setTimeout(() => {
      clearJwt()
      setSession(currentSession())
    }, remaining)

    return () => window.clearTimeout(timer)
  }, [session.jwt, session.jwtExpiresAt])

  const value = useMemo(() => ({
    jwt: session.jwt,
    apiKey: session.apiKey,
    jwtExpiresAt: session.jwtExpiresAt,
    isJwtExpired: !session.jwt || isJwtExpired(session.jwt),
    storageAvailable,
    setJwt: (jwt) => {
      saveJwt(jwt)
      setSession(currentSession())
    },
    setApiKey: (apiKey) => {
      saveApiKey(apiKey)
      setSession(currentSession())
    },
    clearJwt: () => {
      clearJwt()
      setSession(currentSession())
    },
    clearApiKey: () => {
      clearApiKey()
      setSession(currentSession())
    },
    logout: () => {
      clearSession()
      setSession(currentSession())
    },
    refresh: () => setSession(currentSession())
  }), [session, storageAvailable])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession () {
  const context = useContext(SessionContext)
  if (!context) {
    throw new Error('useSession debe utilizarse dentro de SessionProvider')
  }
  return context
}

export function useOptionalSession () {
  return useContext(SessionContext)
}
