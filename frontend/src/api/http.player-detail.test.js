import { afterEach, describe, expect, it, vi } from 'vitest'
import { getPlayerDetail } from './http.js'

describe('getPlayerDetail', () => {
  afterEach(() => vi.restoreAllMocks())

  it('codifica el identificador y conserva el envoltorio data', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers({ 'content-type': 'application/json' }), json: vi.fn().mockResolvedValue({ data: { id: 'ply/a' } }) })
    await expect(getPlayerDetail('ply/a')).resolves.toEqual({ data: { id: 'ply/a' } })
    expect(fetch).toHaveBeenCalledWith('/api/v1/players/ply%2Fa', expect.anything())
  })
})
