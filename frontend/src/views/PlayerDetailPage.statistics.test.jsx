import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PlayerDetailPage from './PlayerDetailPage.jsx'

const detail = {
  id: 'ply-1', name: 'Jugador Completo', league: 'Premier League', team: 'Equipo A',
  position: 'Forward', nationality: 'Argentina',
  statistics: {
    generales: { minsPlayed: 900, rating: 7.2, yellowCard: 2, redCard: 1 },
    ofensivas: { goal: 8, assistTotal: 4, shotsPerGame: 2.3, keyPassPerGame: 1.1, dribbleWonPerGame: 3.4 },
    defensivas: { tacklePerGame: 1.9 },
    pases: { totalPassesPerGame: 58.2, passSuccess: 86.44 }
  }
}

function response (body, status = 200) {
  return { ok: status >= 200 && status < 300, status, headers: new Headers({ 'content-type': 'application/json' }), json: vi.fn().mockResolvedValue(body) }
}

describe('detalle con estadisticas completas', () => {
  afterEach(() => vi.restoreAllMocks())

  it('muestra las cuatro familias y la precision como porcentaje', async () => {
    global.fetch = vi.fn().mockResolvedValue(response({ data: detail }))
    render(<PlayerDetailPage playerId="ply-1" onNavigate={vi.fn()} />)
    await screen.findByRole('heading', { name: 'Jugador Completo', level: 1 })
    fireEvent.click(screen.getByRole('tab', { name: 'Pases' }))
    expect(screen.getByText('86,44')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Pases' })).toHaveAttribute('aria-selected', 'true')
  })

  it('muestra en Resumen las diez métricas principales combinadas', async () => {
    global.fetch = vi.fn().mockResolvedValue(response({ data: detail }))
    const { container } = render(<PlayerDetailPage playerId="ply-1" onNavigate={vi.fn()} />)

    await screen.findByRole('heading', { name: 'Jugador Completo', level: 1 })
    expect(container.querySelectorAll('.statistic-card')).toHaveLength(10)
    for (const label of ['Minutos jugados', 'Goles', 'Asistencias', 'Tiros', 'Pases clave', 'Regates', 'Tackles', 'Valoración', 'Tarjetas amarillas', 'Tarjetas rojas']) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    }
    expect(screen.getAllByText('900').length).toBeGreaterThan(0)
    expect(screen.getAllByText('2,3').length).toBeGreaterThan(0)
    expect(screen.getAllByText('2').length).toBeGreaterThan(0)
    expect(screen.getByText('Valorización Devchester')).toBeInTheDocument()
    expect(within(screen.getByText('Valorización Devchester').parentElement).getByText('-')).toBeInTheDocument()
  })

  it('mantiene el estado de ausencia de estadisticas', async () => {
    global.fetch = vi.fn().mockResolvedValue(response({ data: { ...detail, statistics: null } }))
    render(<PlayerDetailPage playerId="ply-1" onNavigate={vi.fn()} />)
    expect(await screen.findByText(/no tiene estad/i)).toBeInTheDocument()
  })
})
