import { describe, expect, it } from 'vitest'
import {
  clearSession,
  getJwtExpiration,
  isJwtExpired,
  readSession,
  saveApiKey,
  saveJwt
} from '../src/session/storage.js'

function tokenWithExpiration (expiration) {
  const payload = btoa(JSON.stringify({ exp: expiration }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
  return `header.${payload}.signature`
}

describe('sesión aislada por pestaña', () => {
  it('guarda JWT y API Key en sessionStorage', () => {
    const jwt = tokenWithExpiration(4102444800)
    saveJwt(jwt)
    saveApiKey('dev_key_saved')

    expect(readSession()).toEqual({ jwt, apiKey: 'dev_key_saved' })
    expect(window.localStorage.length).toBe(0)
  })

  it('detecta y elimina un JWT expirado sin revocar la API Key local', () => {
    const expired = tokenWithExpiration(100)
    saveJwt(expired)
    saveApiKey('dev_key_survives')

    expect(getJwtExpiration(expired)).toBe(100)
    expect(isJwtExpired(expired, 101000)).toBe(true)
    expect(readSession()).toEqual({ jwt: null, apiKey: 'dev_key_survives' })
  })

  it('limpia las credenciales con logout local', () => {
    saveJwt(tokenWithExpiration(4102444800))
    saveApiKey('dev_key_saved')
    clearSession()

    expect(readSession()).toEqual({ jwt: null, apiKey: null })
  })
})
