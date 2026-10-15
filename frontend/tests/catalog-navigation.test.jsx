import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../src/App.jsx'
import CatalogPage from '../src/views/CatalogPage.jsx'
import PlayerDetailPage from '../src/views/PlayerDetailPage.jsx'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

const catalogResponse = (page = 3) => jsonResponse({
  data: {
    players: [{ id: 'ply-1', name: 'Jugador Uno', league: 'Premier League', team: 'Manchester United FC', position: 'Defender', nationality: 'Argentina' }],
    pagination: { current_page: page, total_pages: 4, total_records: 4 }
  }
})

describe('navegación con estado del catálogo', () => {
  afterEach(() => vi.restoreAllMocks())

  it('restaura filtros y página desde los query params', async () => {
    window.history.replaceState({}, '', '/catalogo?league=Premier%20League&team=Manchester%20United%20FC&position=Defender&page=3')
    global.fetch = vi.fn().mockResolvedValue(catalogResponse())

    render(<CatalogPage onNavigate={vi.fn()} />)

    expect(await screen.findByDisplayValue('Premier League')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Manchester United FC')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Defender')).toBeInTheDocument()
    expect(screen.getByText('Página 3 de 4')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/v1/players?league=Premier+League&team=Manchester+United+FC&position=Defender&page=3&limit=20', expect.anything())
  })

  it('actualiza la URL al cambiar de página y vuelve al catálogo con el mismo estado', async () => {
    window.history.replaceState({}, '', '/catalogo?team=Manchester%20United%20FC')
    global.fetch = vi.fn()
      .mockResolvedValueOnce(catalogResponse(1))
      .mockResolvedValueOnce(catalogResponse(2))
      .mockResolvedValueOnce(jsonResponse({ data: { name: 'Jugador Uno', league: 'Premier League', team: 'Manchester United FC', position: 'Defender' } }))
      .mockResolvedValueOnce(catalogResponse(2))

    render(<App />)
    await screen.findByText('Jugador Uno')
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    await waitFor(() => expect(window.location.search).toBe('?team=Manchester+United+FC&page=2'))
    fireEvent.click(screen.getByRole('link', { name: /Jugador Uno/i }))
    expect(window.location.pathname).toBe('/catalogo/jugadores/ply-1')

    fireEvent.click(await screen.findByRole('link', { name: /Volver al catálogo/i }))

    expect(window.location.pathname).toBe('/catalogo')
    expect(window.location.search).toBe('?team=Manchester+United+FC&page=2')
  })

  it('un acceso directo al detalle vuelve al catálogo sin filtros', async () => {
    window.history.replaceState({}, '', '/catalogo/jugadores/ply-direct')
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { name: 'Jugador Directo', league: 'Liga', team: 'Equipo', position: 'Forward' } }))
    const onNavigate = vi.fn()

    render(<PlayerDetailPage playerId="ply-direct" onNavigate={onNavigate} />)
    fireEvent.click(await screen.findByRole('link', { name: /Volver al catálogo/i }))

    expect(onNavigate).toHaveBeenCalledWith('/catalogo')
  })
})
