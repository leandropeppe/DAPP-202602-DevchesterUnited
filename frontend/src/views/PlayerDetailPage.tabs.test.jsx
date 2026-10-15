import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PlayerDetailPage from './PlayerDetailPage.jsx'

const data = { name: 'Jugador', league: 'Liga', team: 'Equipo', position: 'Forward', nationality: 'Argentina', statistics: {
  generales: { minsPlayed: 1 }, ofensivas: { goal: 2 }, defensivas: { tacklePerGame: 3 }, pases: { passSuccess: 86.44 }
} }

describe('pestanas de estadisticas', () => {
  afterEach(() => vi.restoreAllMocks())

  it('activa cada familia mediante controles tab semanticos', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers({ 'content-type': 'application/json' }), json: vi.fn().mockResolvedValue({ data }) })
    render(<PlayerDetailPage playerId="ply-1" onNavigate={vi.fn()} />)
    await screen.findByRole('heading', { name: 'Jugador', level: 1 })
    fireEvent.click(screen.getByRole('tab', { name: 'Ataque' }))
    expect(screen.getAllByText('Goles').length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('tab', { name: 'Defensa' }))
    expect(screen.getByText('Tackles')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: 'Pases' }))
    expect(screen.getByText('Precisión de pases')).toBeInTheDocument()
  })
})
