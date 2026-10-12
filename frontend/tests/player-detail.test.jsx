import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PlayerDetailPage from '../src/views/PlayerDetailPage.jsx'

function jsonResponse (body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body)
  }
}

const player = {
  id: 'ply-1',
  name: 'Jugador Uno',
  league: 'LaLiga',
  team: 'Madrid',
  position: 'Forward',
  nationality: 'Argentina',
  statistics: {
    position: 'Forward',
    minutes_played: 1234,
    goals: 12,
    assists: 7,
    shots: 2.34,
    key_passes: 1.23,
    dribbles: 3.45,
    tackles: 4.56,
    rating: 7.89,
    yellow_cards: 2,
    red_cards: 1
  }
}

describe('detalle frontend de jugador', () => {
  afterEach(() => vi.restoreAllMocks())

  it('muestra carga, datos basicos y las metricas persistidas', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: player }))
    const onNavigate = vi.fn()

    render(<PlayerDetailPage playerId="ply-1" onNavigate={onNavigate} />)

    expect(screen.getByRole('status')).toHaveTextContent('Cargando')
    expect(await screen.findByRole('heading', { name: 'Jugador Uno' })).toBeInTheDocument()
    expect(screen.getByText('Argentina')).toBeInTheDocument()
    expect(screen.getByText('1.234')).toBeInTheDocument()
    expect(screen.getByText('Valoracion')).toBeInTheDocument()
    expect(screen.getByText('7,89')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Volver al catalogo/i })).toHaveAttribute('href', '/catalogo')
  })

  it('informa cuando el jugador no tiene estadisticas', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { ...player, statistics: null } }))

    render(<PlayerDetailPage playerId="ply-1" onNavigate={vi.fn()} />)

    expect(await screen.findByText(/no tiene estadisticas disponibles/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Jugador Uno' })).toBeInTheDocument()
  })

  it('muestra error y permite regresar al catalogo', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Jugador no encontrado' }, 404))
    const onNavigate = vi.fn()

    render(<PlayerDetailPage playerId="ply-missing" onNavigate={onNavigate} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Jugador no encontrado')
    await waitFor(() => expect(screen.getByRole('link', { name: /Volver al catalogo/i })).toBeInTheDocument())
  })
})
