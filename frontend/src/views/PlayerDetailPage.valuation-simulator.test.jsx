import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PlayerDetailPage from './PlayerDetailPage.jsx'

const detail = {
  id: 'ply-1', name: 'Jugador Completo', league: 'Premier League', team: 'Equipo A', position: 'Forward', nationality: 'Argentina',
  statistics: { generales: { minsPlayed: 900, rating: 7.2 }, ofensivas: { goal: 8, assistTotal: 4 } }
}

const config = {
  playerId: 'ply-1', positions: ['Forward'], resolvedPosition: 'Forward', strategyId: 'POSITIONAL', strategyVersion: 2,
  provisionalStrategy: false, currentQuote: 100, horizons: [3, 5, 8, 10], currentApps: 10, currentMinutes: 900,
  blockingReasons: [], statistics: [{ metric: 'GOALS', label: 'Goles', kind: 'ACCUMULATIVE', currentValue: 8, editable: true, min: 0, max: null }]
}

function response (body) {
  return { ok: true, status: 200, headers: new Headers({ 'content-type': 'application/json' }), json: vi.fn().mockResolvedValue(body) }
}

describe('simulador integrado al detalle', () => {
  afterEach(() => vi.restoreAllMocks())

  it('abre y cierra el modal sin abandonar PlayerDetailPage', async () => {
    global.fetch = vi.fn()
      .mockResolvedValueOnce(response({ data: detail }))
      .mockResolvedValueOnce(response({ data: config }))
    render(<PlayerDetailPage playerId="ply-1" onNavigate={vi.fn()} />)
    await screen.findByRole('heading', { name: 'Jugador Completo', level: 1 })
    fireEvent.click(screen.getByRole('button', { name: 'Simular cotización' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar simulador' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Jugador Completo', level: 1 })).toBeInTheDocument()
  })
})
