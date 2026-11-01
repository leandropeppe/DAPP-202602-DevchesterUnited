import { afterEach, describe, expect, it, vi } from 'vitest'
import { getMarketP2POffers } from './http.js'

describe('getMarketP2POffers playerName', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('envía playerName y conserva sólo filtros soportados', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ data: {} })
    })
    vi.stubGlobal('fetch', fetchMock)

    await getMarketP2POffers({ filters: { playerName: 'valverde', page: 1, unsupported: 'ignored' } })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/market/p2p/offers?playerName=valverde&page=1',
      expect.objectContaining({ method: 'GET' })
    )
  })
})
