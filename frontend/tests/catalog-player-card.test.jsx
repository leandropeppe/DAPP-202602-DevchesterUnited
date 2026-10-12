import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CatalogPage from '../src/views/CatalogPage.jsx'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

describe('cards del catalogo', () => {
  afterEach(() => vi.restoreAllMocks())

  it('muestra nacionalidad, omite dorsal y navega al detalle', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({
      data: {
        players: [{
          id: 'ply-1',
          name: 'Jugador Uno',
          league: 'LaLiga',
          team: 'Madrid',
          position: 'Forward',
          nationality: 'Argentina',
          jersey_number: null
        }],
        pagination: { current_page: 1, total_pages: 1, total_records: 1 }
      }
    }))
    const onNavigate = vi.fn()

    render(<CatalogPage onNavigate={onNavigate} />)

    const playerLink = await screen.findByRole('link', { name: /Jugador Uno/i })
    expect(screen.getByText('Argentina')).toBeInTheDocument()
    expect(screen.queryByText(/Dorsal/i)).not.toBeInTheDocument()
    expect(screen.queryByText('0')).not.toBeInTheDocument()
    fireEvent.click(playerLink)
    expect(onNavigate).toHaveBeenCalledWith('/catalogo/jugadores/ply-1')
  })
})
