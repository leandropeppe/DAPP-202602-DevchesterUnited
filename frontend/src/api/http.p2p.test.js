import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cancelMarketP2POffer,
  getMarketP2POffers,
  publishMarketP2POffer,
  purchaseMarketP2POffer
} from './http.js'

function okResponse (payload) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(payload)
  }
}

describe('clientes HTTP del mercado P2P', () => {
  afterEach(() => vi.restoreAllMocks())

  it('lista ofertas con filtros y paginación del contrato', async () => {
    global.fetch = vi.fn().mockResolvedValue(okResponse({ data: { offers: [], pagination: {} } }))
    await getMarketP2POffers({ jwt: 'jwt', filters: { status: 'SUSPENDED', mine: 'true', page: 2, limit: 10 } })
    expect(fetch).toHaveBeenCalledWith('/api/v1/market/p2p/offers?status=SUSPENDED&mine=true&page=2&limit=10', expect.objectContaining({ headers: expect.any(Headers) }))
  })

  it('envía Idempotency-Key en publicación, compra y cancelación', async () => {
    global.fetch = vi.fn()
      .mockResolvedValueOnce(okResponse({ data: {} }))
      .mockResolvedValueOnce(okResponse({ data: {} }))
      .mockResolvedValueOnce(okResponse({ data: {} }))

    await publishMarketP2POffer({ jwt: 'jwt', idempotencyKey: 'publish-key', body: { player_id: 'p', quantity: 2 } })
    await purchaseMarketP2POffer({ jwt: 'jwt', offerId: 'offer/a', idempotencyKey: 'purchase-key', body: { quantity: 1, quote_id: 'q', quote_version: 1 } })
    await cancelMarketP2POffer({ jwt: 'jwt', offerId: 'offer/a', idempotencyKey: 'cancel-key' })

    expect(fetch.mock.calls[0][1].headers.get('Idempotency-Key')).toBe('publish-key')
    expect(fetch.mock.calls[1][0]).toBe('/api/v1/market/p2p/offers/offer%2Fa/purchases')
    expect(fetch.mock.calls[1][1].headers.get('Idempotency-Key')).toBe('purchase-key')
    expect(fetch.mock.calls[2][1].headers.get('Idempotency-Key')).toBe('cancel-key')
  })

  it('conserva código y current_quote de errores de negocio', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: vi.fn().mockResolvedValue({ message: 'La cotización cambió.', code: 'QUOTE_CHANGED', current_quote: null })
    })
    await expect(getMarketP2POffers({ jwt: 'jwt' })).rejects.toMatchObject({
      status: 409,
      code: 'QUOTE_CHANGED',
      currentQuote: null
    })
  })
})
