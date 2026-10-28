import { afterEach, describe, expect, it, vi } from 'vitest'
import { getValuationSimulatorConfig, simulatePlayerValuation } from './http.js'

function response (body, status = 200) {
  return { ok: status >= 200 && status < 300, status, headers: new Headers({ 'content-type': 'application/json' }), json: vi.fn().mockResolvedValue(body) }
}

describe('cliente HTTP del simulador', () => {
  afterEach(() => vi.restoreAllMocks())

  it('obtiene la configuración del endpoint posicional', async () => {
    global.fetch = vi.fn().mockResolvedValue(response({ data: { horizons: [3, 5, 8, 10] } }))
    await expect(getValuationSimulatorConfig('ply/1', 'jwt-123')).resolves.toEqual({ data: { horizons: [3, 5, 8, 10] } })
    expect(fetch).toHaveBeenCalledWith('/api/v1/players/ply%2F1/valuation-simulator', expect.objectContaining({ method: 'GET' }))
    expect(fetch.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer jwt-123')
  })

  it('envía únicamente horizonte y modificaciones', async () => {
    global.fetch = vi.fn().mockResolvedValue(response({ data: { simulatedQuote: 120 } }))
    await simulatePlayerValuation('ply-1', { horizonMatches: 3, modifications: { GOALS: 2 } }, 'jwt-123')
    const options = fetch.mock.calls[0][1]
    expect(JSON.parse(options.body)).toEqual({ horizonMatches: 3, modifications: { GOALS: 2 } })
    expect(JSON.parse(options.body)).not.toHaveProperty('currentQuote')
    expect(options.headers.get('Authorization')).toBe('Bearer jwt-123')
  })

  it.each([400, 409])('propaga errores HTTP %s', async (status) => {
    global.fetch = vi.fn().mockResolvedValue(response({ message: 'Bloqueado' }, status))
    await expect(getValuationSimulatorConfig('ply-1')).rejects.toMatchObject({ status, message: 'Bloqueado' })
  })
})
