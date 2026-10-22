import { afterEach, describe, expect, it, vi } from 'vitest'
import { getPlayers, getPlayerValuationHistory, HttpError, register, request } from '../src/api/http.js'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

describe('cliente HTTP Devchester', () => {
  afterEach(() => vi.restoreAllMocks())

  it('usa rutas relativas y cabeceras según la operación', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success' }))

    await request('/players?page=1', { jwt: 'jwt-no-usado', apiKey: 'dev_key_test' })

    expect(fetch).toHaveBeenCalledWith('/api/v1/players?page=1', expect.objectContaining({
      headers: expect.any(Headers)
    }))
    const headers = fetch.mock.calls[0][1].headers
    expect(headers.get('X-API-KEY')).toBe('dev_key_test')
    expect(headers.get('Authorization')).toBe('Bearer jwt-no-usado')
    expect(headers.get('Accept')).toBe('application/json')
  })

  it('serializa el registro como JSON y no construye URLs externas', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success' }))

    await register({ username: 'ana123', email: 'ana@example.com', password: 'ClaveSegura1' })

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/auth/register')
    expect(fetch.mock.calls[0][1].body).toContain('ana@example.com')
    expect(fetch.mock.calls[0][1].headers.get('Content-Type')).toBe('application/json')
  })

  it('construye filtros y paginación del catálogo', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success', data: { players: [], pagination: {} } }))

    await getPlayers({ filters: { team: 'Madrid', position: '', page: 2, limit: 20 } })

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/players?team=Madrid&page=2&limit=20')
    expect(fetch.mock.calls[0][1].headers.get('Authorization')).toBeNull()
    expect(fetch.mock.calls[0][1].headers.get('X-API-KEY')).toBeNull()
  })

  it.each([400, 401, 409, 503])('expone de forma uniforme el error HTTP %s', async (status) => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'error', message: `error-${status}` }, status))

    await expect(request('/auth/login')).rejects.toMatchObject({
      name: 'HttpError',
      status,
      message: `error-${status}`
    })
  })

  it('consulta el histórico mediante la ruta quotes', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success', data: { items: [] } }))

    await getPlayerValuationHistory('ply-1', { page: 2, limit: 10, strategyId: 'POSITIONAL', strategyVersion: 2 })

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/players/ply-1/quotes?page=2&limit=10&strategyId=POSITIONAL&strategyVersion=2')
  })

  it('traduce un fallo de red en 503', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('network'))

    await expect(request('/players')).rejects.toBeInstanceOf(HttpError)
    await expect(request('/players')).rejects.toMatchObject({ status: 503 })
  })
})
