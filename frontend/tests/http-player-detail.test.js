import { afterEach, describe, expect, it, vi } from 'vitest'
import { getPlayerDetail, HttpError } from '../src/api/http.js'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

describe('cliente HTTP del detalle de jugador', () => {
  afterEach(() => vi.restoreAllMocks())

  it('consulta el endpoint con el identificador publico opaco', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { id: 'ply-1' } }))

    await expect(getPlayerDetail('ply-1')).resolves.toEqual({ data: { id: 'ply-1' } })
    expect(fetch).toHaveBeenCalledWith('/api/v1/players/ply-1', expect.anything())
  })

  it('propaga errores HTTP normalizados', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Jugador no encontrado' }, 404))

    await expect(getPlayerDetail('ply-missing')).rejects.toMatchObject({
      status: 404,
      message: 'Jugador no encontrado'
    })
    await expect(getPlayerDetail('ply-missing')).rejects.toBeInstanceOf(HttpError)
  })
})
