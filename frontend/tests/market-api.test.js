import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getMarketBalance,
  getMarketOperation,
  getMarketPlayers,
  getMarketPlayer,
  getMarketPortfolio,
  getMarketTransactions,
  purchaseMarketTokens,
  sellMarketTokens
} from '../src/api/http.js'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

describe('cliente HTTP del Token Market', () => {
  afterEach(() => vi.restoreAllMocks())

  it('consulta saldo y jugadores con JWT y paginación', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success', data: {} }))

    await getMarketBalance('jwt-balance')
    await getMarketPlayers({ jwt: 'jwt-players', filters: { page: 2, limit: 20 } })

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/market/balance')
    expect(fetch.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer jwt-balance')
    expect(fetch.mock.calls[1][0]).toBe('/api/v1/market/players?page=2&limit=20')
    expect(fetch.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer jwt-players')
  })

  it('consulta un jugador puntual del mercado', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success', data: {} }))

    await getMarketPlayer('11111111-1111-4111-8111-111111111111', 'jwt-player')

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/market/players/11111111-1111-4111-8111-111111111111')
    expect(fetch.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer jwt-player')
  })

  it.each([
    ['purchaseMarketTokens', purchaseMarketTokens, '/api/v1/market/purchases'],
    ['sellMarketTokens', sellMarketTokens, '/api/v1/market/sales']
  ])('envía %s con payload, JWT e Idempotency-Key', async (_name, operation, path) => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success', data: {} }))
    const body = { player_id: '11111111-1111-4111-8111-111111111111', quantity: 2, quote_id: '22222222-2222-4222-8222-222222222222', quote_version: 4 }

    await operation({ body, idempotencyKey: 'market-key-1', jwt: 'jwt-operation' })

    expect(fetch.mock.calls[0][0]).toBe(path)
    expect(fetch.mock.calls[0][1].method).toBe('POST')
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(body)
    expect(fetch.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer jwt-operation')
    expect(fetch.mock.calls[0][1].headers.get('Idempotency-Key')).toBe('market-key-1')
  })

  it('recupera una operación usando la misma clave codificada', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success', data: {} }))

    await getMarketOperation('key with spaces', 'jwt-recovery')

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/market/operations/key%20with%20spaces')
    expect(fetch.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer jwt-recovery')
  })

  it('consulta portfolio e historial sin aceptar userId como filtro', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ status: 'success', data: {} }))

    await getMarketPortfolio('jwt-private')
    await getMarketTransactions({
      jwt: 'jwt-private',
      filters: {
        playerId: '11111111-1111-4111-8111-111111111111',
        type: 'SALE',
        from: '2026-10-01T00:00:00Z',
        to: '2026-11-01T00:00:00Z',
        page: 2,
        limit: 20,
        userId: 'other-user'
      }
    })

    expect(fetch.mock.calls[0][0]).toBe('/api/v1/market/portfolio')
    expect(fetch.mock.calls[1][0]).toContain('/api/v1/market/transactions?')
    expect(fetch.mock.calls[1][0]).toContain('playerId=11111111-1111-4111-8111-111111111111')
    expect(fetch.mock.calls[1][0]).not.toContain('userId')
    expect(fetch.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer jwt-private')
  })
})
